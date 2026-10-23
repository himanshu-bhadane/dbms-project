package dental.dao;

import dental.db.Sql;
import dental.db.Sql.RowMapper;
import dental.model.Models.Appointment;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

public final class AppointmentDao {
    private static final RowMapper<Appointment> M = rs -> new Appointment(
            rs.getInt("AppointmentID"), rs.getDate("AppointmentDate").toLocalDate(), Sql.time(rs, "AppointmentTime"),
            rs.getString("AppointmentType"), rs.getString("AppointmentStatus"), rs.getString("BookingChannel"),
            rs.getString("ChiefComplaint"), rs.getInt("PatientID"), rs.getInt("DoctorID"),
            rs.getInt("ClinicID"), rs.getInt("OperatoryID"));

    public int insert(Connection c, Appointment a) throws SQLException {
        return Sql.insert(c, "INSERT INTO APPOINTMENT (AppointmentDate,AppointmentTime,AppointmentType,AppointmentStatus,"
                + "BookingChannel,ChiefComplaint,PatientID,DoctorID,ClinicID,OperatoryID) VALUES (?,?,?,?,?,?,?,?,?,?)",
                a.date(), a.time(), a.type(), a.status(), a.channel(), a.complaint(),
                a.patientId(), a.doctorId(), a.clinicId(), a.operatoryId());
    }

    public Optional<Appointment> findById(Connection c, int id) throws SQLException {
        return Sql.one(c, "SELECT * FROM APPOINTMENT WHERE AppointmentID=?", M, id);
    }

    public int updateStatus(Connection c, int id, String status) throws SQLException {
        return Sql.update(c, "UPDATE APPOINTMENT SET AppointmentStatus=? WHERE AppointmentID=?", status, id);
    }

    public int reschedule(Connection c, int id, LocalDate d, LocalTime t) throws SQLException {
        return Sql.update(c, "UPDATE APPOINTMENT SET AppointmentDate=?, AppointmentTime=? WHERE AppointmentID=?", d, t, id);
    }

    public int delete(Connection c, int id) throws SQLException {
        return Sql.update(c, "DELETE FROM APPOINTMENT WHERE AppointmentID=?", id);
    }

    // ---- slot-conflict checks (cancelled appointments don't hold a slot) ----
    private static final String LIVE = " AND AppointmentStatus<>'Cancelled' AND AppointmentID<>?";

    public boolean doctorBusy(Connection c, int doctorId, LocalDate d, LocalTime t, int excludeId) throws SQLException {
        return Sql.exists(c, "SELECT 1 FROM APPOINTMENT WHERE DoctorID=? AND AppointmentDate=? AND AppointmentTime=?" + LIVE,
                doctorId, d, t, excludeId);
    }

    public boolean operatoryBusy(Connection c, int operatoryId, LocalDate d, LocalTime t, int excludeId) throws SQLException {
        return Sql.exists(c, "SELECT 1 FROM APPOINTMENT WHERE OperatoryID=? AND AppointmentDate=? AND AppointmentTime=?" + LIVE,
                operatoryId, d, t, excludeId);
    }

    public boolean patientBusy(Connection c, int patientId, LocalDate d, LocalTime t, int excludeId) throws SQLException {
        return Sql.exists(c, "SELECT 1 FROM APPOINTMENT WHERE PatientID=? AND AppointmentDate=? AND AppointmentTime=?" + LIVE,
                patientId, d, t, excludeId);
    }
}
