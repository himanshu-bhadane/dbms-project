package dental.app;
import dental.auth.PasswordHasher;
import dental.db.Db;
import dental.db.Sql;
import java.sql.Connection;
import java.util.List;

public final class SeedUsers {
    static final String PW_DOCTOR = "Doctor@123", PW_RECEPTION = "Recep@123", PW_PATIENT = "Patient@123";
    static final String PW_ADMIN = "Admin@123", ADMIN_EMAIL = "jai.kota@instantdental.com";

    public static void main(String[] args) throws Exception {
        try (Connection c = Db.get()) {
            c.setAutoCommit(false);
            int d = 0, r = 0, p = 0, a = 0;
            
            List<Object[]> docs = Sql.query(c, "SELECT DoctorID, Email FROM DOCTOR",
                    rs -> new Object[]{rs.getInt(1), rs.getString(2)});
            for (Object[] x : docs)
                d += add(c, (String) x[1], PW_DOCTOR, "DOCTOR", "DoctorID", (int) x[0]);
                
            List<Object[]> staff = Sql.query(c, "SELECT EmployeeID, Email FROM EMPLOYEE WHERE Designation='Receptionist'",
                    rs -> new Object[]{rs.getInt(1), rs.getString(2)});
            for (Object[] x : staff)
                r += add(c, (String) x[1], PW_RECEPTION, "RECEPTION", "EmployeeID", (int) x[0]);
                
            List<Object[]> pats = Sql.query(c, "SELECT PatientID, PhoneNumber FROM PATIENT",
                    rs -> new Object[]{rs.getInt(1), rs.getString(2)});
            for (Object[] x : pats)
                p += add(c, (String) x[1], PW_PATIENT, "PATIENT", "PatientID", (int) x[0]);
            
            for (Object[] x : Sql.query(c, "SELECT EmployeeID, Email FROM EMPLOYEE WHERE Email=?",
                    rs -> new Object[]{rs.getInt(1), rs.getString(2)}, ADMIN_EMAIL))
                a += add(c, (String) x[1], PW_ADMIN, "ADMIN", "EmployeeID", (int) x[0]);

            c.commit();
            System.out.printf("Created: %d doctors, %d receptionists, %d patients, %d admin%n", d, r, p, a);
        }
    }

    private static int add(Connection c, String username, String pw, String role, String linkCol, int linkId) throws Exception {
        if (Sql.exists(c, "SELECT 1 FROM USER_ACCOUNT WHERE Username=?", username)) return 0;
        Sql.update(c, "INSERT INTO USER_ACCOUNT (Username, PasswordHash, Role, " + linkCol + ") VALUES (?,?,?,?)",
                username, PasswordHasher.hash(pw), role, linkId);
        return 1;
    }
}