package dental.service;

import dental.dao.*;
import dental.db.Db;
import dental.db.Table;
import dental.model.Models.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.format.TextStyle;
import java.util.*;

/**
 * Business layer. Every public method = one DB transaction (commit on success, rollback on any failure).
 * All rules from the Assignment 1 spec that the schema alone can't express live here.
 */
public final class DentalService {

    // enumerations (mirror the CHECK constraints in 01_schema_mysql.sql)
    public static final Set<String> TYPES    = Set.of("In-Clinic", "Home Visit", "Video Consultation");
    public static final Set<String> CHANNELS = Set.of("Reception", "Call Center", "Patient Portal");
    public static final Set<String> PAY_MODES = Set.of("Cash", "Card", "UPI", "Wallet");
    public static final Set<String> PAY_STATUS = Set.of("Paid", "Pending");
    public static final Set<String> OP_STATUS = Set.of("Available", "Occupied");
    private static final Set<String> DAYS = Set.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday");

    /** Appointment life-cycle: Confirmed -> Checked-In -> Show ; Confirmed/Checked-In -> Cancelled ; Confirmed -> No-Show. */
    private static final Map<String, Set<String>> NEXT = Map.of(
            "Confirmed", Set.of("Checked-In", "No-Show", "Cancelled"),
            "Checked-In", Set.of("Show", "Cancelled"),
            "Show", Set.of(), "No-Show", Set.of(), "Cancelled", Set.of());

    private final PatientDao patients = new PatientDao();
    private final DoctorDao doctors = new DoctorDao();
    private final ClinicDao clinics = new ClinicDao();
    private final EmployeeDao employees = new EmployeeDao();
    private final OperatoryDao operatories = new OperatoryDao();
    private final ScheduleDao schedules = new ScheduleDao();
    private final AppointmentDao appts = new AppointmentDao();
    private final TreatmentDao treatments = new TreatmentDao();
    private final PaymentDao payments = new PaymentDao();
    private final ReportDao reports = new ReportDao();

    // ------------------------------------------------------------------ tx plumbing
    @FunctionalInterface private interface Work<T> { T run(Connection c) throws SQLException; }

    private <T> T tx(Work<T> w) {
        try (Connection c = Db.get()) {
            c.setAutoCommit(false);
            try {
                T r = w.run(c);
                c.commit();
                return r;
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    private static BusinessException translate(SQLException e) {
        return switch (e.getErrorCode()) {
            case 1062 -> new BusinessException("Duplicate value - a unique field (phone / email / receipt) already exists.");
            case 1451 -> new BusinessException("Cannot delete: other records still reference this row.");
            case 1452 -> new BusinessException("Referenced record does not exist (foreign-key violation).");
            case 3819 -> new BusinessException("Value rejected by a CHECK constraint.");
            default -> new BusinessException("Database error [" + e.getErrorCode() + "]: " + e.getMessage());
        };
    }

    private static void need(boolean ok, String msg) { if (!ok) throw new BusinessException(msg); }
    private static void blank(String s, String field) { need(s != null && !s.isBlank(), field + " is required"); }
    private static void in(String v, Set<String> allowed, String field) {
        need(v != null && allowed.contains(v), field + " must be one of " + new TreeSet<>(allowed));
    }
    private static String weekday(LocalDate d) { return d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH); }

    // ------------------------------------------------------------------ patients
    public int registerPatient(String name, String gender, LocalDate dob, String phone, String email, String address,
                               String ecName, String ecNumber, String history) {
        blank(name, "Name"); blank(gender, "Gender"); blank(address, "Address");
        need(dob != null && !dob.isAfter(LocalDate.now()), "Date of birth must be in the past");
        need(phone != null && phone.matches("\\d{10}"), "Phone must be 10 digits");
        need(email == null || email.contains("@"), "Email looks invalid");
        need(ecNumber == null || ecNumber.matches("\\d{10}"), "Emergency number must be 10 digits");
        int age = Period.between(dob, LocalDate.now()).getYears();   // Age is derived, never typed by the user
        return tx(c -> {
            need(patients.findByPhone(c, phone).isEmpty(), "A patient with this phone number already exists");
            return patients.insert(c, new Patient(0, name.trim(), gender, dob, age, phone, email, address.trim(), ecName, ecNumber, history));
        });
    }

    public void updatePatientContact(int id, String phone, String email, String address) {
        need(phone != null && phone.matches("\\d{10}"), "Phone must be 10 digits");
        blank(address, "Address");
        tx(c -> {
            need(patients.findById(c, id).isPresent(), "Patient not found");
            patients.findByPhone(c, phone).ifPresent(p -> need(p.id() == id, "Phone already belongs to another patient"));
            patients.updateContact(c, id, phone, email, address);
            return null;
        });
    }

    public void deletePatient(int id) {
        tx(c -> { need(patients.delete(c, id) == 1, "Patient not found"); return null; });
    }

    public Optional<Patient> patientById(int id)       { return tx(c -> patients.findById(c, id)); }
    public Optional<Patient> patientByPhone(String ph) { return tx(c -> patients.findByPhone(c, ph)); }
    public List<Patient> patientsByName(String frag)   { return tx(c -> patients.searchByName(c, frag)); }
    public List<Patient> allPatients()                 { return tx(patients::all); }

    // ------------------------------------------------------------------ doctors & schedules
    public int addDoctor(String name, String spec, String qual, String contact, String email) {
        blank(name, "Name"); blank(spec, "Specialisation"); blank(qual, "Qualification");
        need(contact != null && contact.matches("\\d{10}"), "Contact must be 10 digits");
        need(email != null && email.contains("@"), "Valid email required");
        return tx(c -> doctors.insert(c, new Doctor(0, name.trim(), spec, qual, contact, email)));
    }

    public void deleteDoctor(int id) {
        tx(c -> { need(doctors.delete(c, id) == 1, "Doctor not found"); return null; });
    }

    public List<Doctor> allDoctors()              { return tx(doctors::all); }
    public List<Doctor> doctorsOfClinic(int cid)  { return tx(c -> doctors.byClinic(c, cid)); }

    public int addSchedule(int doctorId, int clinicId, String day, LocalTime start, LocalTime end) {
        in(day, DAYS, "Day");
        need(start != null && end != null && end.isAfter(start), "End time must be after start time");
        return tx(c -> {
            need(doctors.findById(c, doctorId).isPresent(), "Doctor not found");
            need(clinics.findById(c, clinicId).isPresent(), "Clinic not found");
            need(!schedules.overlaps(c, doctorId, day, start, end), "Doctor already has an overlapping slot on " + day);
            return schedules.insert(c, new Schedule(0, doctorId, clinicId, day, start, end, true));
        });
    }

    /** Leave handling: mark a roster slot unavailable (false) / available (true). Blocks new bookings in that slot. */
    public void setScheduleAvailable(int scheduleId, boolean available) {
        tx(c -> { need(schedules.setAvailable(c, scheduleId, available) == 1, "Schedule not found"); return null; });
    }

    public void deleteSchedule(int scheduleId) {
        tx(c -> { need(schedules.delete(c, scheduleId) == 1, "Schedule not found"); return null; });
    }

    public List<Schedule> schedulesOfDoctor(int doctorId) { return tx(c -> schedules.byDoctor(c, doctorId)); }

    // ------------------------------------------------------------------ clinics, operatories, employees
    public int addClinic(String name, String address, String contact, String hours, String category) {
        blank(name, "Name"); blank(address, "Address"); blank(contact, "Contact"); blank(hours, "Hours"); blank(category, "Category");
        return tx(c -> clinics.insert(c, new Clinic(0, name.trim(), address.trim(), contact, hours, category)));
    }

    public List<Clinic> allClinics() { return tx(clinics::all); }

    public int addOperatory(int clinicId, String type, String status) {
        blank(type, "Type"); in(status, OP_STATUS, "Status");
        return tx(c -> {
            need(clinics.findById(c, clinicId).isPresent(), "Clinic not found");
            return operatories.insert(c, new Operatory(0, type, status, clinicId));
        });
    }

    public void setOperatoryStatus(int id, String status) {
        in(status, OP_STATUS, "Status");
        tx(c -> { need(operatories.setStatus(c, id, status) == 1, "Operatory not found"); return null; });
    }

    public List<Operatory> operatoriesOfClinic(int clinicId) { return tx(c -> operatories.byClinic(c, clinicId)); }

    public int addEmployee(String name, String designation, BigDecimal salary, String email, String phone, int clinicId) {
        blank(name, "Name"); blank(designation, "Designation");
        need(salary != null && salary.signum() > 0, "Salary must be positive");
        need(email != null && email.contains("@"), "Valid email required");
        need(phone != null && phone.matches("\\d{10}"), "Phone must be 10 digits");
        return tx(c -> {
            need(clinics.findById(c, clinicId).isPresent(), "Clinic not found");
            return employees.insert(c, new Employee(0, name.trim(), designation, salary, email, phone, clinicId));
        });
    }

    public void updateEmployeeSalary(int id, BigDecimal salary) {
        need(salary != null && salary.signum() > 0, "Salary must be positive");
        tx(c -> { need(employees.updateSalary(c, id, salary) == 1, "Employee not found"); return null; });
    }

    public void deleteEmployee(int id) {
        tx(c -> { need(employees.delete(c, id) == 1, "Employee not found"); return null; });
    }

    public List<Employee> employeesOfClinic(int clinicId) { return tx(c -> employees.byClinic(c, clinicId)); }

    // ------------------------------------------------------------------ appointments
    /**
     * Book an appointment. Rules enforced:
     *  - patient / doctor / clinic / operatory exist, and the operatory belongs to the chosen clinic
     *  - date is not in the past; type & channel are valid; status starts as 'Confirmed'
     *  - the doctor is rostered AND available at that clinic on that weekday/time (DOCTOR_SCHEDULE)
     *  - no double-booking of doctor, operatory or patient for the same date+time (cancelled slots are free)
     * Doctor & operatory rows are locked first, so two receptionists can't grab the same slot concurrently.
     */
    public int book(int patientId, int doctorId, int clinicId, int operatoryId, LocalDate date, LocalTime time,
                    String type, String channel, String complaint) {
        in(type, TYPES, "Type"); in(channel, CHANNELS, "Channel");
        need(date != null && time != null, "Date and time are required");
        need(!date.isBefore(LocalDate.now()), "Cannot book in the past");
        return tx(c -> {
            doctors.lock(c, doctorId);          // lock first -> later reads see latest committed data
            operatories.lock(c, operatoryId);
            need(patients.findById(c, patientId).isPresent(), "Patient not found");
            need(doctors.findById(c, doctorId).isPresent(), "Doctor not found");
            need(clinics.findById(c, clinicId).isPresent(), "Clinic not found");
            Operatory op = operatories.findById(c, operatoryId).orElseThrow(() -> new BusinessException("Operatory not found"));
            need(op.clinicId() == clinicId, "Operatory " + operatoryId + " does not belong to clinic " + clinicId);
            checkSlot(c, patientId, doctorId, clinicId, operatoryId, date, time, 0);
            return appts.insert(c, new Appointment(0, date, time, type, "Confirmed", channel, complaint,
                    patientId, doctorId, clinicId, operatoryId));
        });
    }

    private void checkSlot(Connection c, int patientId, int doctorId, int clinicId, int operatoryId,
                           LocalDate d, LocalTime t, int excludeId) throws SQLException {
        need(schedules.covers(c, doctorId, clinicId, weekday(d), t),
                "Doctor is not rostered/available at this clinic on " + weekday(d) + " at " + t);
        need(!appts.doctorBusy(c, doctorId, d, t, excludeId), "Doctor already has an appointment at that time");
        need(!appts.operatoryBusy(c, operatoryId, d, t, excludeId), "Operatory is already booked at that time");
        need(!appts.patientBusy(c, patientId, d, t, excludeId), "Patient already has an appointment at that time");
    }

    /** Status workflow: Confirmed -> Checked-In|No-Show|Cancelled ; Checked-In -> Cancelled. ('Show' happens via completeVisit.) */
    public void changeStatus(int appointmentId, String newStatus) {
        need(!"Show".equals(newStatus), "Use 'complete visit' to mark Show - it records the treatment and payment too");
        tx(c -> {
            Appointment a = appts.findById(c, appointmentId).orElseThrow(() -> new BusinessException("Appointment not found"));
            need(NEXT.getOrDefault(a.status(), Set.of()).contains(newStatus),
                    "Illegal transition " + a.status() + " -> " + newStatus + " (allowed: " + NEXT.get(a.status()) + ")");
            appts.updateStatus(c, appointmentId, newStatus);
            return null;
        });
    }

    public void reschedule(int appointmentId, LocalDate newDate, LocalTime newTime) {
        need(newDate != null && newTime != null && !newDate.isBefore(LocalDate.now()), "New slot must be today or later");
        tx(c -> {
            Appointment a = appts.findById(c, appointmentId).orElseThrow(() -> new BusinessException("Appointment not found"));
            need("Confirmed".equals(a.status()), "Only Confirmed appointments can be rescheduled (current: " + a.status() + ")");
            doctors.lock(c, a.doctorId());
            operatories.lock(c, a.operatoryId());
            checkSlot(c, a.patientId(), a.doctorId(), a.clinicId(), a.operatoryId(), newDate, newTime, appointmentId);
            appts.reschedule(c, appointmentId, newDate, newTime);
            return null;
        });
    }

    /**
     * Finish a visit atomically: appointment -> Show, insert TREATMENT, insert PAYMENT (amount = treatment cost),
     * generate the receipt number. All-or-nothing. Returns the receipt number.
     */
    public String completeVisit(int appointmentId, String performed, String diagnosis, String notes, String medicines,
                                String followUp, BigDecimal cost, String payMode, String payStatus) {
        blank(performed, "Treatment performed"); blank(diagnosis, "Diagnosis");
        need(cost != null && cost.signum() >= 0, "Cost must be >= 0");
        in(payMode, PAY_MODES, "Payment mode"); in(payStatus, PAY_STATUS, "Payment status");
        return tx(c -> {
            Appointment a = appts.findById(c, appointmentId).orElseThrow(() -> new BusinessException("Appointment not found"));
            need("Checked-In".equals(a.status()), "Patient must be Checked-In first (current: " + a.status() + ")");
            need(treatments.findByAppointment(c, appointmentId).isEmpty(), "Treatment already recorded");
            need(payments.findByAppointment(c, appointmentId).isEmpty(), "Payment already recorded");
            appts.updateStatus(c, appointmentId, "Show");
            treatments.insert(c, new Treatment(0, appointmentId, performed, diagnosis, notes, medicines, followUp, cost));
            String temp = "TMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            int payId = payments.insert(c, new Payment(0, appointmentId, cost, LocalDate.now(), payMode, payStatus, temp));
            String receipt = "RCPT" + (LocalDate.now().getYear() * 1000L + payId);
            payments.setReceipt(c, payId, receipt);
            return receipt;
        });
    }

    /** Settle a pending payment (Pending -> Paid). */
    public void settlePayment(int appointmentId) {
        tx(c -> {
            Payment p = payments.findByAppointment(c, appointmentId).orElseThrow(() -> new BusinessException("No payment for this appointment"));
            need("Pending".equals(p.status()), "Payment is already " + p.status());
            payments.setStatus(c, p.id(), "Paid");
            return null;
        });
    }

    public Optional<Appointment> appointmentById(int id) { return tx(c -> appts.findById(c, id)); }
    public Optional<Treatment> treatmentOf(int appointmentId) { return tx(c -> treatments.findByAppointment(c, appointmentId)); }
    public Optional<Payment> paymentOf(int appointmentId) { return tx(c -> payments.findByAppointment(c, appointmentId)); }

    // ------------------------------------------------------------------ search & reports
    public Table searchAppointments(Integer patientId, Integer doctorId, Integer clinicId, LocalDate date) {
        return tx(c -> reports.searchAppointments(c, patientId, doctorId, clinicId, date));
    }
    public Table dailySchedule(LocalDate d, Integer clinicId) { return tx(c -> reports.dailySchedule(c, d, clinicId)); }
    public Table doctorSchedule(int doctorId)                  { return tx(c -> reports.doctorSchedule(c, doctorId)); }
    public Table patientHistory(int patientId)                 { return tx(c -> reports.patientHistory(c, patientId)); }
    public Table clinicRevenue(LocalDate from, LocalDate to)   { return tx(c -> reports.clinicRevenue(c, from, to)); }
    public Table paymentSummary(LocalDate from, LocalDate to)  { return tx(c -> reports.paymentSummary(c, from, to)); }
    public Table doctorWorkload(LocalDate from, LocalDate to)  { return tx(c -> reports.doctorWorkload(c, from, to)); }
}
