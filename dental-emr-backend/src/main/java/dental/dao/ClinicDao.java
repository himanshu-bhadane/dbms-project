package dental.dao;

import dental.db.Sql;
import dental.db.Sql.RowMapper;
import dental.model.Models.Clinic;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class ClinicDao {
    private static final RowMapper<Clinic> M = rs -> new Clinic(
            rs.getInt("ClinicID"), rs.getString("ClinicName"), rs.getString("Address"),
            rs.getString("ContactNumber"), rs.getString("OperatingHours"), rs.getString("ClinicCategory"));

    public int insert(Connection c, Clinic x) throws SQLException {
        return Sql.insert(c, "INSERT INTO CLINIC (ClinicName,Address,ContactNumber,OperatingHours,ClinicCategory) VALUES (?,?,?,?,?)",
                x.name(), x.address(), x.contact(), x.hours(), x.category());
    }

    public int delete(Connection c, int id) throws SQLException {
        return Sql.update(c, "DELETE FROM CLINIC WHERE ClinicID=?", id);
    }

    public Optional<Clinic> findById(Connection c, int id) throws SQLException {
        return Sql.one(c, "SELECT * FROM CLINIC WHERE ClinicID=?", M, id);
    }

    public List<Clinic> all(Connection c) throws SQLException {
        return Sql.query(c, "SELECT * FROM CLINIC ORDER BY ClinicID", M);
    }
}
