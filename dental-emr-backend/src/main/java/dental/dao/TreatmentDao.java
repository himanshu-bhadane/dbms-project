package dental.dao;

import dental.db.Sql;
import dental.db.Sql.RowMapper;
import dental.model.Models.Treatment;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public final class TreatmentDao {
    private static final RowMapper<Treatment> M = rs -> new Treatment(
            rs.getInt("TreatmentID"), rs.getInt("AppointmentID"), rs.getString("TreatmentPerformed"),
            rs.getString("Diagnosis"), rs.getString("ClinicalNotes"), rs.getString("PrescribedMedicines"),
            rs.getString("FollowUpRecommendations"), rs.getBigDecimal("TreatmentCost"));

    public int insert(Connection c, Treatment t) throws SQLException {
        return Sql.insert(c, "INSERT INTO TREATMENT (AppointmentID,TreatmentPerformed,Diagnosis,ClinicalNotes,"
                + "PrescribedMedicines,FollowUpRecommendations,TreatmentCost) VALUES (?,?,?,?,?,?,?)",
                t.appointmentId(), t.performed(), t.diagnosis(), t.notes(), t.medicines(), t.followUp(), t.cost());
    }

    public Optional<Treatment> findByAppointment(Connection c, int appointmentId) throws SQLException {
        return Sql.one(c, "SELECT * FROM TREATMENT WHERE AppointmentID=?", M, appointmentId);
    }
}
