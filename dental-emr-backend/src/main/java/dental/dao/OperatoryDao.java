package dental.dao;

import dental.db.Sql;
import dental.db.Sql.RowMapper;
import dental.model.Models.Operatory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class OperatoryDao {
    private static final RowMapper<Operatory> M = rs -> new Operatory(
            rs.getInt("OperatoryID"), rs.getString("OperatoryType"), rs.getString("AvailabilityStatus"), rs.getInt("ClinicID"));

    public int insert(Connection c, Operatory o) throws SQLException {
        return Sql.insert(c, "INSERT INTO OPERATORY (OperatoryType,AvailabilityStatus,ClinicID) VALUES (?,?,?)",
                o.type(), o.status(), o.clinicId());
    }

    public int setStatus(Connection c, int id, String status) throws SQLException {
        return Sql.update(c, "UPDATE OPERATORY SET AvailabilityStatus=? WHERE OperatoryID=?", status, id);
    }

    public Optional<Operatory> findById(Connection c, int id) throws SQLException {
        return Sql.one(c, "SELECT * FROM OPERATORY WHERE OperatoryID=?", M, id);
    }

    public List<Operatory> byClinic(Connection c, int clinicId) throws SQLException {
        return Sql.query(c, "SELECT * FROM OPERATORY WHERE ClinicID=? ORDER BY OperatoryID", M, clinicId);
    }

    public void lock(Connection c, int id) throws SQLException {
        Sql.query(c, "SELECT OperatoryID FROM OPERATORY WHERE OperatoryID=? FOR UPDATE", rs -> 1, id);
    }
}
