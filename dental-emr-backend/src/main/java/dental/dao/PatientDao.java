package dental.dao;

import dental.db.Sql;
import dental.db.Sql.RowMapper;
import dental.model.Models.Patient;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class PatientDao {
    private static final RowMapper<Patient> M = rs -> new Patient(
            rs.getInt("PatientID"), rs.getString("PatientName"), rs.getString("Gender"),
            rs.getDate("DateOfBirth").toLocalDate(), rs.getInt("Age"), rs.getString("PhoneNumber"),
            rs.getString("Email"), rs.getString("Address"), rs.getString("EmergencyContactName"),
            rs.getString("EmergencyContactNumber"), rs.getString("MedicalHistory"));

    public int insert(Connection c, Patient p) throws SQLException {
        return Sql.insert(c, "INSERT INTO PATIENT (PatientName,Gender,DateOfBirth,Age,PhoneNumber,Email,Address,"
                + "EmergencyContactName,EmergencyContactNumber,MedicalHistory) VALUES (?,?,?,?,?,?,?,?,?,?)",
                p.name(), p.gender(), p.dob(), p.age(), p.phone(), p.email(), p.address(),
                p.ecName(), p.ecNumber(), p.history());
    }

    public int updateContact(Connection c, int id, String phone, String email, String address) throws SQLException {
        return Sql.update(c, "UPDATE PATIENT SET PhoneNumber=?, Email=?, Address=? WHERE PatientID=?", phone, email, address, id);
    }

    public int delete(Connection c, int id) throws SQLException {
        return Sql.update(c, "DELETE FROM PATIENT WHERE PatientID=?", id);
    }

    public Optional<Patient> findById(Connection c, int id) throws SQLException {
        return Sql.one(c, "SELECT * FROM PATIENT WHERE PatientID=?", M, id);
    }

    public Optional<Patient> findByPhone(Connection c, String phone) throws SQLException {
        return Sql.one(c, "SELECT * FROM PATIENT WHERE PhoneNumber=?", M, phone);
    }

    public List<Patient> searchByName(Connection c, String fragment) throws SQLException {
        return Sql.query(c, "SELECT * FROM PATIENT WHERE PatientName LIKE ? ORDER BY PatientName", M, "%" + fragment + "%");
    }

    public List<Patient> all(Connection c) throws SQLException {
        return Sql.query(c, "SELECT * FROM PATIENT ORDER BY PatientID", M);
    }
}
