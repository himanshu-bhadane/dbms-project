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
          + " a.AppointmentStatus AS Status, a.ChiefComplaint AS Complaint";

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

    /** Report 3: full visit history of a patient with treatment + payment. */
    public Table patientHistory(Connection c, int patientId) throws SQLException {
        return Sql.table(c, "SELECT a.AppointmentID AS ID, a.AppointmentDate AS Date, d.DoctorName AS Doctor, cl.ClinicName AS Clinic,"
                + " a.AppointmentStatus AS Status, t.TreatmentPerformed AS Treatment, t.Diagnosis, t.TreatmentCost AS Cost,"
                + " pay.PaymentStatus AS Payment, t.FollowUpRecommendations AS FollowUp"
                + " FROM APPOINTMENT a JOIN DOCTOR d ON d.DoctorID=a.DoctorID JOIN CLINIC cl ON cl.ClinicID=a.ClinicID"
                + " LEFT JOIN TREATMENT t ON t.AppointmentID=a.AppointmentID LEFT JOIN PAYMENT pay ON pay.AppointmentID=a.AppointmentID"
                + " WHERE a.PatientID=? ORDER BY a.AppointmentDate DESC, a.AppointmentTime DESC", patientId);
    }

    /** Report 4: clinic-wise revenue in a date range (every clinic listed, even with zero). */
    public Table clinicRevenue(Connection c, LocalDate from, LocalDate to) throws SQLException {
        return Sql.table(c, "SELECT cl.ClinicID AS ID, cl.ClinicName AS Clinic, COUNT(a.AppointmentID) AS Appointments,"
                + " COUNT(pay.PaymentID) AS Payments,"
                + " COALESCE(SUM(CASE WHEN pay.PaymentStatus='Paid' THEN pay.Amount END),0) AS Collected,"
                + " COALESCE(SUM(CASE WHEN pay.PaymentStatus='Pending' THEN pay.Amount END),0) AS Pending"
                + " FROM CLINIC cl LEFT JOIN APPOINTMENT a ON a.ClinicID=cl.ClinicID AND a.AppointmentDate BETWEEN ? AND ?"
                + " LEFT JOIN PAYMENT pay ON pay.AppointmentID=a.AppointmentID"
                + " GROUP BY cl.ClinicID, cl.ClinicName ORDER BY Collected DESC", from, to);
    }

    /** Report 5: payment summary by mode and status. */
    public Table paymentSummary(Connection c, LocalDate from, LocalDate to) throws SQLException {
        return Sql.table(c, "SELECT PaymentMode AS Mode, PaymentStatus AS Status, COUNT(*) AS Payments, SUM(Amount) AS Total,"
                + " ROUND(AVG(Amount),2) AS Average FROM PAYMENT WHERE PaymentDate BETWEEN ? AND ?"
                + " GROUP BY PaymentMode, PaymentStatus ORDER BY PaymentMode, PaymentStatus", from, to);
    }

    /** Extra: doctor-wise workload and revenue. */
    public Table doctorWorkload(Connection c, LocalDate from, LocalDate to) throws SQLException {
        return Sql.table(c, "SELECT d.DoctorID AS ID, d.DoctorName AS Doctor, d.Specialisation,"
                + " COUNT(a.AppointmentID) AS Appointments, SUM(a.AppointmentStatus='Show') AS Completed,"
                + " SUM(a.AppointmentStatus='No-Show') AS NoShows, SUM(a.AppointmentStatus='Cancelled') AS Cancelled,"
                + " COALESCE(SUM(CASE WHEN pay.PaymentStatus='Paid' THEN pay.Amount END),0) AS Revenue"
                + " FROM DOCTOR d LEFT JOIN APPOINTMENT a ON a.DoctorID=d.DoctorID AND a.AppointmentDate BETWEEN ? AND ?"
                + " LEFT JOIN PAYMENT pay ON pay.AppointmentID=a.AppointmentID"
                + " GROUP BY d.DoctorID, d.DoctorName, d.Specialisation ORDER BY Revenue DESC", from, to);
    }
}
