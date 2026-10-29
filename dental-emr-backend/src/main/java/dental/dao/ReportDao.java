package dental.dao;

import dental.db.Sql;
import dental.db.Table;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Joined searches + the five reports required in Assignment 1. */
public final class ReportDao {

    private static final String APPT_JOIN =
            " FROM APPOINTMENT a JOIN PATIENT p ON p.PatientID=a.PatientID JOIN DOCTOR d ON d.DoctorID=a.DoctorID"
          + " JOIN CLINIC cl ON cl.ClinicID=a.ClinicID JOIN OPERATORY o ON o.OperatoryID=a.OperatoryID ";

    private static final String APPT_COLS =
            "SELECT a.AppointmentID AS ID, a.AppointmentDate AS Date, a.AppointmentTime AS Time, p.PatientName AS Patient,"
          + " d.DoctorName AS Doctor, cl.ClinicName AS Clinic, o.OperatoryType AS Room, a.AppointmentType AS Type,"
          + " a.AppointmentStatus AS Status, a.ChiefComplaint AS Complaint, a.PatientID AS PatientID,"
          + " a.DoctorID AS DoctorID, a.ClinicID AS ClinicID";

    /** Search appointments by any combination of patient / doctor / clinic / date (null = don't filter). */
    public Table searchAppointments(Connection c, Integer patientId, Integer doctorId, Integer clinicId, LocalDate date) throws SQLException {
        StringBuilder sql = new StringBuilder(APPT_COLS).append(APPT_JOIN).append("WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (patientId != null) { sql.append(" AND a.PatientID=?"); args.add(patientId); }
        if (doctorId != null)  { sql.append(" AND a.DoctorID=?");  args.add(doctorId); }
        if (clinicId != null)  { sql.append(" AND a.ClinicID=?");  args.add(clinicId); }
        if (date != null)      { sql.append(" AND a.AppointmentDate=?"); args.add(date); }
        sql.append(" ORDER BY a.AppointmentDate, a.AppointmentTime LIMIT 200");
        return Sql.table(c, sql.toString(), args.toArray());
    }

    /** Report 1: daily appointment schedule (optionally one clinic). */
    public Table dailySchedule(Connection c, LocalDate date, Integer clinicId) throws SQLException {
        StringBuilder sql = new StringBuilder(APPT_COLS).append(APPT_JOIN).append("WHERE a.AppointmentDate=?");
        List<Object> args = new ArrayList<>(List.of(date));
        if (clinicId != null) { sql.append(" AND a.ClinicID=?"); args.add(clinicId); }
        sql.append(" ORDER BY cl.ClinicName, a.AppointmentTime");
        return Sql.table(c, sql.toString(), args.toArray());
    }

    /** Report 2: weekly roster of one doctor across clinics. */
    public Table doctorSchedule(Connection c, int doctorId) throws SQLException {
        return Sql.table(c, "SELECT s.ScheduleID AS ID, s.DayOfWeek AS Day, s.StartTime AS Start, s.EndTime AS End,"
                + " cl.ClinicName AS Clinic, IF(s.IsAvailable,'Available','On leave') AS Availability"
                + " FROM DOCTOR_SCHEDULE s JOIN CLINIC cl ON cl.ClinicID=s.ClinicID WHERE s.DoctorID=?"
                + " ORDER BY FIELD(s.DayOfWeek,'Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday'), s.StartTime", doctorId);
    }

    /** Report 3: full visit history of a patient with treatment + payment (optionally only visits at one clinic). */
    public Table patientHistory(Connection c, int patientId, Integer clinicId) throws SQLException {
        List<Object> args = new ArrayList<>(List.of(patientId));
        String sql = "SELECT a.AppointmentID AS ID, a.AppointmentDate AS Date, d.DoctorName AS Doctor, cl.ClinicName AS Clinic,"
                + " a.AppointmentStatus AS Status, t.TreatmentPerformed AS Treatment, t.Diagnosis, t.TreatmentCost AS Cost,"
                + " pay.PaymentStatus AS Payment, pay.ReceiptNumber AS Receipt, t.FollowUpRecommendations AS FollowUp"
                + " FROM APPOINTMENT a JOIN DOCTOR d ON d.DoctorID=a.DoctorID JOIN CLINIC cl ON cl.ClinicID=a.ClinicID"
                + " LEFT JOIN TREATMENT t ON t.AppointmentID=a.AppointmentID LEFT JOIN PAYMENT pay ON pay.AppointmentID=a.AppointmentID"
                + " WHERE a.PatientID=?";
        if (clinicId != null) { sql += " AND a.ClinicID=?"; args.add(clinicId); }
        sql += " ORDER BY a.AppointmentDate DESC, a.AppointmentTime DESC";
        return Sql.table(c, sql, args.toArray());
    }

    /** Report 4: clinic-wise revenue in a date range (all clinics, or just one). */
    public Table clinicRevenue(Connection c, LocalDate from, LocalDate to, Integer clinicId) throws SQLException {
        List<Object> args = new ArrayList<>(List.of(from, to));
        String sql = "SELECT cl.ClinicID AS ID, cl.ClinicName AS Clinic, COUNT(a.AppointmentID) AS Appointments,"
                + " COUNT(pay.PaymentID) AS Payments,"
                + " COALESCE(SUM(CASE WHEN pay.PaymentStatus='Paid' THEN pay.Amount END),0) AS Collected,"
                + " COALESCE(SUM(CASE WHEN pay.PaymentStatus='Pending' THEN pay.Amount END),0) AS Pending"
                + " FROM CLINIC cl LEFT JOIN APPOINTMENT a ON a.ClinicID=cl.ClinicID AND a.AppointmentDate BETWEEN ? AND ?"
                + " LEFT JOIN PAYMENT pay ON pay.AppointmentID=a.AppointmentID";
        if (clinicId != null) { sql += " WHERE cl.ClinicID=?"; args.add(clinicId); }
        sql += " GROUP BY cl.ClinicID, cl.ClinicName ORDER BY Collected DESC";
        return Sql.table(c, sql, args.toArray());
    }

    /** Report 5: payment summary by mode and status (payments of appointments at one clinic, or all). */
    public Table paymentSummary(Connection c, LocalDate from, LocalDate to, Integer clinicId) throws SQLException {
        List<Object> args = new ArrayList<>(List.of(from, to));
        String sql = "SELECT pay.PaymentMode AS Mode, pay.PaymentStatus AS Status, COUNT(*) AS Payments, SUM(pay.Amount) AS Total,"
                + " ROUND(AVG(pay.Amount),2) AS Average FROM PAYMENT pay JOIN APPOINTMENT a ON a.AppointmentID=pay.AppointmentID"
                + " WHERE pay.PaymentDate BETWEEN ? AND ?";
        if (clinicId != null) { sql += " AND a.ClinicID=?"; args.add(clinicId); }
        sql += " GROUP BY pay.PaymentMode, pay.PaymentStatus ORDER BY pay.PaymentMode, pay.PaymentStatus";
        return Sql.table(c, sql, args.toArray());
    }

    /** Extra: doctor-wise workload and revenue. With a clinic: only doctors rostered there, only that clinic's appointments. */
    public Table doctorWorkload(Connection c, LocalDate from, LocalDate to, Integer clinicId) throws SQLException {
        List<Object> args = new ArrayList<>(List.of(from, to));
        String sql = "SELECT d.DoctorID AS ID, d.DoctorName AS Doctor, d.Specialisation,"
                + " COUNT(a.AppointmentID) AS Appointments, SUM(a.AppointmentStatus='Show') AS Completed,"
                + " SUM(a.AppointmentStatus='No-Show') AS NoShows, SUM(a.AppointmentStatus='Cancelled') AS Cancelled,"
                + " COALESCE(SUM(CASE WHEN pay.PaymentStatus='Paid' THEN pay.Amount END),0) AS Revenue"
                + " FROM DOCTOR d LEFT JOIN APPOINTMENT a ON a.DoctorID=d.DoctorID AND a.AppointmentDate BETWEEN ? AND ?";
        if (clinicId != null) { sql += " AND a.ClinicID=?"; args.add(clinicId); }
        sql += " LEFT JOIN PAYMENT pay ON pay.AppointmentID=a.AppointmentID";
        if (clinicId != null) { sql += " WHERE d.DoctorID IN (SELECT DoctorID FROM DOCTOR_SCHEDULE WHERE ClinicID=?)"; args.add(clinicId); }
        sql += " GROUP BY d.DoctorID, d.DoctorName, d.Specialisation ORDER BY Revenue DESC";
        return Sql.table(c, sql, args.toArray());
    }
}
