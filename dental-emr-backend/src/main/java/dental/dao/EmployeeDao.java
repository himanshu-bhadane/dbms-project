package dental.dao;

import dental.db.Sql;
import dental.db.Sql.RowMapper;
import dental.model.Models.Employee;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public final class EmployeeDao {
    private static final RowMapper<Employee> M = rs -> new Employee(
            rs.getInt("EmployeeID"), rs.getString("EmployeeName"), rs.getString("Designation"),
            rs.getBigDecimal("Salary"), rs.getString("Email"), rs.getString("PhoneNumber"), rs.getInt("AssignedClinicID"));

    public int insert(Connection c, Employee e) throws SQLException {
        return Sql.insert(c, "INSERT INTO EMPLOYEE (EmployeeName,Designation,Salary,Email,PhoneNumber,AssignedClinicID) VALUES (?,?,?,?,?,?)",
                e.name(), e.designation(), e.salary(), e.email(), e.phone(), e.clinicId());
    }

    public int updateSalary(Connection c, int id, BigDecimal salary) throws SQLException {
        return Sql.update(c, "UPDATE EMPLOYEE SET Salary=? WHERE EmployeeID=?", salary, id);
    }

    public int transfer(Connection c, int id, int clinicId) throws SQLException {
        return Sql.update(c, "UPDATE EMPLOYEE SET AssignedClinicID=? WHERE EmployeeID=?", clinicId, id);
    }

    public int delete(Connection c, int id) throws SQLException {
        return Sql.update(c, "DELETE FROM EMPLOYEE WHERE EmployeeID=?", id);
    }

    public List<Employee> byClinic(Connection c, int clinicId) throws SQLException {
        return Sql.query(c, "SELECT * FROM EMPLOYEE WHERE AssignedClinicID=? ORDER BY EmployeeName", M, clinicId);
    }
}
