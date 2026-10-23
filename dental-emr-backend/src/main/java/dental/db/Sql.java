package dental.db;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Tiny PreparedStatement helper so DAOs stay short. Every value is bound - no string concatenation of user input. */
public final class Sql {
    private Sql() {}

    @FunctionalInterface
    public interface RowMapper<T> { T map(ResultSet rs) throws SQLException; }

    private static void bind(PreparedStatement ps, Object[] a) throws SQLException {
        for (int i = 0; i < a.length; i++) {
            Object v = a[i];
            if (v instanceof LocalDate d) ps.setDate(i + 1, Date.valueOf(d));
            else if (v instanceof LocalTime t) ps.setTime(i + 1, Time.valueOf(t));
            else ps.setObject(i + 1, v);
        }
    }

    public static <T> List<T> query(Connection c, String sql, RowMapper<T> m, Object... a) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, a);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) out.add(m.map(rs));
                return out;
            }
        }
    }

    public static <T> Optional<T> one(Connection c, String sql, RowMapper<T> m, Object... a) throws SQLException {
        List<T> l = query(c, sql, m, a);
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    public static boolean exists(Connection c, String sql, Object... a) throws SQLException {
        return !query(c, sql, rs -> 1, a).isEmpty();
    }

    public static int update(Connection c, String sql, Object... a) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, a);
            return ps.executeUpdate();
        }
    }

    /** INSERT and return the AUTO_INCREMENT key. */
    public static int insert(Connection c, String sql, Object... a) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, a);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                return k.next() ? k.getInt(1) : -1;
            }
        }
    }

    public static Table table(Connection c, String sql, Object... a) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, a);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int n = md.getColumnCount();
                List<String> cols = new ArrayList<>();
                for (int i = 1; i <= n; i++) cols.add(md.getColumnLabel(i));
                List<List<String>> rows = new ArrayList<>();
                while (rs.next()) {
                    List<String> r = new ArrayList<>();
                    for (int i = 1; i <= n; i++) { Object o = rs.getObject(i); r.add(o == null ? "-" : o.toString()); }
                    rows.add(r);
                }
                return new Table(cols, rows);
            }
        }
    }

    public static LocalTime time(ResultSet rs, String col) throws SQLException {
        Time t = rs.getTime(col);
        return t == null ? null : t.toLocalTime();
    }
}
