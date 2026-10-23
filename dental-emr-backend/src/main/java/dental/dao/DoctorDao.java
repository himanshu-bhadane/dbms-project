package dental.dao;

import dental.db.Sql;
import dental.db.Sql.RowMapper;
import dental.model.Models.Doctor;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class DoctorDao {
    private static final RowMapper<Doctor> M = rs -> new Doctor(
            rs.getInt("DoctorID"), rs.getString("DoctorName"), rs.getString("Specialisation"),
            rs.getString("Qualification"), rs.getString("ContactNumber"), rs.getString("Email"));

    public int insert(Connection c, Doctor d) throws SQLException {
        return Sql.insert(c, "INSERT INTO DOCTOR (DoctorName,Specialisation,Qualification,ContactNumber,Email) VALUES (?,?,?,?,?)",
                d.name(), d.specialisation(), d.qualification(), d.contact(), d.email());
    }

    public int updateContact(Connection c, int id, String contact, String email) throws SQLException {
        return Sql.update(c, "UPDATE DOCTOR SET ContactNumber=?, Email=? WHERE DoctorID=?", contact, email, id);
    }

    public int delete(Connection c, int id) throws SQLException {
        return Sql.update(c, "DELETE FROM DOCTOR WHERE DoctorID=?", id);
    }

    public Optional<Doctor> findById(Connection c, int id) throws SQLException {
        return Sql.one(c, "SELECT * FROM DOCTOR WHERE DoctorID=?", M, id);
    }

    public List<Doctor> all(Connection c) throws SQLException {
        return Sql.query(c, "SELECT * FROM DOCTOR ORDER BY DoctorID", M);
    }

    public List<Doctor> byClinic(Connection c, int clinicId) throws SQLException {
        return Sql.query(c, "SELECT DISTINCT d.* FROM DOCTOR d JOIN DOCTOR_SCHEDULE s ON s.DoctorID=d.DoctorID "
                + "WHERE s.ClinicID=? ORDER BY d.DoctorName", M, clinicId);
    }

    /** Row lock: serialises concurrent bookings for the same doctor. */
    public void lock(Connection c, int id) throws SQLException {
        Sql.query(c, "SELECT DoctorID FROM DOCTOR WHERE DoctorID=? FOR UPDATE", rs -> 1, id);
    }
}
