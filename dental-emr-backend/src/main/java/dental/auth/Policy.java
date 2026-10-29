package dental.auth;

import dental.auth.AuthService.Session;
import dental.db.Db;
import dental.db.Sql;
import dental.service.BusinessException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

/**
 * Who may call which endpoint, and on whose data. Default-deny: unknown endpoints are refused.
 * check() may rewrite params (pinning them to the caller's own IDs) so the client cannot widen its scope.
 * RECEPTION is confined to its own clinic; ADMIN is not confined.
 */
public final class Policy {
    private static final String REC = "RECEPTION", DOC = "DOCTOR", PAT = "PATIENT", ADM = "ADMIN";
    private static final Set<String> DOCTOR_STATUSES  = Set.of("Checked-In", "No-Show");
    private static final Set<String> PATIENT_STATUSES = Set.of("Cancelled");
    private Policy() {}

    public static void check(Session s, String ep, Map<String, String> p) {
        String role = s.role;
        switch (ep) {
            // ---- reference data
            case "clinics", "doctors" -> { }
            case "operatories" -> pinClinic(s, p);
            case "roster"      -> { if (DOC.equals(role)) p.put("doctorId", own(s.doctorId)); }
            case "employees"   -> roles(role, ADM);          // salaries: admin only
                        // ---- admin management
            case "admins", "admin-promote" -> roles(role, ADM);
            case "admin-demote" -> { roles(role, ADM); p.put("actorUserId", String.valueOf(s.userId)); }   // server-supplied, can't be faked

            // ---- patients
            case "patients" -> {
                if (PAT.equals(role)) {
                    p.remove("phone"); p.remove("name");
                    p.put("id", own(s.patientId));
                } else if (DOC.equals(role)) {
                    p.remove("phone"); p.remove("name");
                    needTreats(s, intp(p, "id"));
                }
            }
            case "patient-register" -> roles(role, REC, ADM);
            case "r-history" -> {
                if (PAT.equals(role)) p.put("patientId", own(s.patientId));
                else if (DOC.equals(role)) needTreats(s, intp(p, "patientId"));
                else pinClinic(s, p);                         // reception: only visits at own clinic
            }
            case "profile-update" -> { roles(role, PAT); p.put("id", own(s.patientId)); }

            // ---- roster
            case "roster-add" -> { roles(role, REC, ADM); needClinic(s, intp(p, "clinicId")); }
            case "leave" -> {
                roles(role, REC, DOC, ADM);
                int[] sc = lookup("SELECT DoctorID, ClinicID FROM DOCTOR_SCHEDULE WHERE ScheduleID=?", intp(p, "scheduleId"));
                boolean ok = sc != null && (ADM.equals(role)
                        || (REC.equals(role) ? same(s.clinicId, sc[1]) : same(s.doctorId, sc[0])));
                if (!ok) throw deny();
            }
            

            // ---- appointments
            case "appt-search" -> {
                if (REC.equals(role)) pinClinic(s, p);
                else if (DOC.equals(role)) p.put("doctorId", own(s.doctorId));
                else if (PAT.equals(role)) p.put("patientId", own(s.patientId));
                // ADMIN: unrestricted
            }
            case "appt-book" -> {
                roles(role, REC, PAT, ADM);
                if (PAT.equals(role)) { p.put("patientId", own(s.patientId)); p.put("channel", "Patient Portal"); p.remove("operatoryId"); }
                else needClinic(s, intp(p, "clinicId"));
            }
            case "appt-status" -> {
                needAppt(s, intp(p, "id"));
                String st = p.get("status");
                if (DOC.equals(role) && !allowed(DOCTOR_STATUSES, st) || PAT.equals(role) && !allowed(PATIENT_STATUSES, st))
                    throw new ForbiddenException("Your role cannot set status '" + st + "'");
            }
            case "appt-reschedule" -> { roles(role, REC, PAT, ADM); needAppt(s, intp(p, "id")); }
            case "appt-complete"   -> { roles(role, DOC);           needAppt(s, intp(p, "id")); }
            case "appt-settle"     -> { roles(role, REC, ADM);      needAppt(s, intp(p, "id")); }
            case "slots", "free-rooms" -> { roles(role, REC, ADM, PAT); pinClinic(s, p); }   // patients need these in Step 7

            // ---- reports: reception pinned to own clinic, admin optional clinicId
            case "r-daily", "r-revenue", "r-payments", "r-workload" -> { roles(role, REC, ADM); pinClinic(s, p); }

            default -> throw deny();
        }
    }

    // ------------------------------------------------------------ helpers
    private static ForbiddenException deny() { return new ForbiddenException("Access denied"); }

    private static void roles(String role, String... allowed) {
        for (String a : allowed) if (a.equals(role)) return;
        throw new ForbiddenException("Your role (" + role + ") cannot use this action");
    }

    private static boolean allowed(Set<String> set, String v) { return v != null && set.contains(v); }
    private static boolean same(Integer a, int b) { return a != null && a == b; }

    private static String own(Integer v) {
        if (v == null) throw new ForbiddenException("This account is not linked to a record");
        return v.toString();
    }

    /** Reception: overwrite clinicId with their own. Everyone else: leave as sent. */
    private static void pinClinic(Session s, Map<String, String> p) {
        if (REC.equals(s.role)) p.put("clinicId", own(s.clinicId));
    }

    private static int intp(Map<String, String> p, String k) {
        String v = p.get(k);
        if (v == null || v.isBlank()) throw new BusinessException(k + " is required");
        try { return Integer.parseInt(v.trim()); }
        catch (NumberFormatException e) { throw new BusinessException(k + " must be a number"); }
    }

    private static void needClinic(Session s, int clinicId) {
        if (ADM.equals(s.role)) return;
        if (!same(s.clinicId, clinicId)) throw deny();
    }

    /** Receptionist of the appointment's clinic, its doctor, its patient, or an admin (if the row exists). */
    private static void needAppt(Session s, int apptId) {
        int[] a = lookup("SELECT PatientID, DoctorID, ClinicID FROM APPOINTMENT WHERE AppointmentID=?", apptId);
        if (a != null) {
            if (ADM.equals(s.role)) return;
            if (REC.equals(s.role) && same(s.clinicId, a[2])) return;
            if (DOC.equals(s.role) && same(s.doctorId, a[1])) return;
            if (PAT.equals(s.role) && same(s.patientId, a[0])) return;
        }
        throw deny();
    }

    private static void needTreats(Session s, int patientId) {
        if (s.doctorId == null) throw deny();
        try (Connection c = Db.get()) {
            if (!Sql.exists(c, "SELECT 1 FROM APPOINTMENT WHERE DoctorID=? AND PatientID=? LIMIT 1", s.doctorId, patientId))
                throw deny();
        } catch (SQLException e) {
            throw new BusinessException("Database error during permission check");
        }
    }

    private static int[] lookup(String sql, int id) {
        try (Connection c = Db.get()) {
            return Sql.one(c, sql, rs -> {
                int n = rs.getMetaData().getColumnCount();
                int[] r = new int[n];
                for (int i = 0; i < n; i++) r[i] = rs.getInt(i + 1);
                return r;
            }, id).orElse(null);
        } catch (SQLException e) {
            throw new BusinessException("Database error during permission check");
        }
    }
}