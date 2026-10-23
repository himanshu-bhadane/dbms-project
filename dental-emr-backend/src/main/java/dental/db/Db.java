package dental.db;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** Opens JDBC connections. Config: db.properties, overridable by env DB_URL / DB_USER / DB_PASSWORD. */
public final class Db {
    private static final Properties P = new Properties();
    static {
        try (InputStream in = Db.class.getResourceAsStream("/db.properties")) {
            if (in != null) P.load(in);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    private Db() {}

    private static String cfg(String envKey, String propKey) {
        String v = System.getenv(envKey);
        return (v != null && !v.isBlank()) ? v : P.getProperty(propKey);
    }

    // public static Connection get() throws SQLException {
    //     return DriverManager.getConnection(cfg("DB_URL", "url"), cfg("DB_USER", "user"), cfg("DB_PASSWORD", "password"));
    // }
        public static Connection get() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL driver not on classpath", e);
        }
        return DriverManager.getConnection(cfg("DB_URL", "url"), cfg("DB_USER", "user"), cfg("DB_PASSWORD", "password"));
    }
}
