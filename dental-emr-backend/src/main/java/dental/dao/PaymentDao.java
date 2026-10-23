package dental.dao;

import dental.db.Sql;
import dental.db.Sql.RowMapper;
import dental.model.Models.Payment;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public final class PaymentDao {
    private static final RowMapper<Payment> M = rs -> new Payment(
            rs.getInt("PaymentID"), rs.getInt("AppointmentID"), rs.getBigDecimal("Amount"),
            rs.getDate("PaymentDate").toLocalDate(), rs.getString("PaymentMode"),
            rs.getString("PaymentStatus"), rs.getString("ReceiptNumber"));

    public int insert(Connection c, Payment p) throws SQLException {
        return Sql.insert(c, "INSERT INTO PAYMENT (AppointmentID,Amount,PaymentDate,PaymentMode,PaymentStatus,ReceiptNumber) VALUES (?,?,?,?,?,?)",
                p.appointmentId(), p.amount(), p.date(), p.mode(), p.status(), p.receipt());
    }

    public int setReceipt(Connection c, int id, String receipt) throws SQLException {
        return Sql.update(c, "UPDATE PAYMENT SET ReceiptNumber=? WHERE PaymentID=?", receipt, id);
    }

    public int setStatus(Connection c, int id, String status) throws SQLException {
        return Sql.update(c, "UPDATE PAYMENT SET PaymentStatus=? WHERE PaymentID=?", status, id);
    }

    public Optional<Payment> findByAppointment(Connection c, int appointmentId) throws SQLException {
        return Sql.one(c, "SELECT * FROM PAYMENT WHERE AppointmentID=?", M, appointmentId);
    }

    public Optional<Payment> findById(Connection c, int id) throws SQLException {
        return Sql.one(c, "SELECT * FROM PAYMENT WHERE PaymentID=?", M, id);
    }
}
