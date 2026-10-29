package dental.auth;

import dental.db.Db;
import dental.db.Sql;
import dental.db.Sql.RowMapper;
import dental.service.BusinessException;
import dental.db.Table;
import java.util.List;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Login, in-memory sessions, brute-force throttle. Authorization rules come in Step 3. */
public final class AuthService {
    public static final long IDLE_MS = 30 * 60_000L;      // session dies after 30 min of inactivity
    private static final int MAX_FAILS = 5;
    private static final long LOCK_MS = 5 * 60_000L;
    /** Verified when the username doesn't exist, so "unknown user" and "wrong password" take equal time. */
    private static final String DUMMY_HASH = PasswordHasher.hash("not-a-real-password");
    private static final SecureRandom RNG = new SecureRandom();

    public static final class Session {
        public final String token, username, role;
        public final int userId;
        public final Integer doctorId, patientId, employeeId, clinicId;   // clinicId only for RECEPTION
        volatile long expiresAt;

        Session(String token, int userId, String username, String role,
                Integer doctorId, Integer patientId, Integer employeeId, Integer clinicId) {
            this.token = token; this.userId = userId; this.username = username; this.role = role;
            this.doctorId = doctorId; this.patientId = patientId; this.employeeId = employeeId; this.clinicId = clinicId;
            this.expiresAt = System.currentTimeMillis() + IDLE_MS;
        }

        /** What the browser is allowed to know (never the token or hash). */
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", username);
            m.put("role", role);
            m.put("doctorId", doctorId);
            m.put("patientId", patientId);
            m.put("employeeId", employeeId);
            m.put("clinicId", clinicId);
            return m;
        }
    }

    private record Row(int userId, String username, String hash, String role, boolean active,
                       Integer doctorId, Integer patientId, Integer employeeId, Integer clinicId) {}

    private static final String SQL =
            "SELECT u.UserID, u.Username, u.PasswordHash, u.Role, u.IsActive, u.DoctorID, u.PatientID, u.EmployeeID,"
          + " e.AssignedClinicID FROM USER_ACCOUNT u LEFT JOIN EMPLOYEE e ON e.EmployeeID=u.EmployeeID WHERE u.Username=?";

    private static final RowMapper<Row> ROW = rs -> new Row(
            rs.getInt("UserID"), rs.getString("Username"), rs.getString("PasswordHash"), rs.getString("Role"),
            rs.getBoolean("IsActive"),
            rs.getObject("DoctorID", Integer.class), rs.getObject("PatientID", Integer.class),
            rs.getObject("EmployeeID", Integer.class), rs.getObject("AssignedClinicID", Integer.class));

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, long[]> fails = new ConcurrentHashMap<>();   // username -> {failCount, lockedUntil}
    private final Map<String, long[]> signups = new ConcurrentHashMap<>();   // ip -> {count, windowStart}
    private static final int RESET_MAX_ATTEMPTS = 5;
    private static final long RESET_TTL_MS = 15 * 60_000L, RESET_COOLDOWN_MS = 60_000L;
    private static final String BAD_CODE = "Invalid or expired code";
    private final Map<String, Long> lastReset = new ConcurrentHashMap<>();       // username -> last request time
    private final Map<String, long[]> resetIps = new ConcurrentHashMap<>();     // ip -> {count, windowStart}

    public Session login(String username, String password) {
        String user = username.trim();
        long now = System.currentTimeMillis();
        long[] f = fails.get(user);
        if (f != null && f[1] > now) throw new AuthException("Too many failed attempts - try again in a few minutes");

        Row u;
        try (Connection c = Db.get()) {
            u = Sql.one(c, SQL, ROW, user).orElse(null);
        } catch (SQLException e) {
            throw new BusinessException("Database error during login");
        }

        boolean ok = PasswordHasher.verify(password, u == null ? DUMMY_HASH : u.hash());
        if (u == null || !ok || !u.active()) {
            recordFail(user, now);
            throw new AuthException("Invalid username or password");   // same message for every failure reason
        }
        fails.remove(user);
        sessions.values().removeIf(s -> s.expiresAt < now);            // housekeeping

        byte[] t = new byte[32];
        RNG.nextBytes(t);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(t);
        Session s = new Session(token, u.userId(), u.username(), u.role(),
                u.doctorId(), u.patientId(), u.employeeId(),
                "ADMIN".equals(u.role()) ? null : u.clinicId());
        sessions.put(token, s);
        return s;
    }

    /** Returns the live session (and slides its expiry) or throws AuthException. */
    public Session require(String token) {
        Session s = token == null ? null : sessions.get(token);
        long now = System.currentTimeMillis();
        if (s == null || s.expiresAt < now) {
            if (s != null) sessions.remove(token);
            throw new AuthException("Not logged in");
        }
        s.expiresAt = now + IDLE_MS;
        return s;
    }

    public void logout(String token) { if (token != null) sessions.remove(token); }

    private void recordFail(String user, long now) {
        if (fails.size() > 10_000) fails.clear();   // stop unknown usernames from growing the map forever
        fails.compute(user, (k, v) -> {
            long[] a = v == null ? new long[2] : v;
            if (a[1] != 0 && a[1] <= now) { a[0] = 0; a[1] = 0; }   // previous lock expired
            if (++a[0] >= MAX_FAILS) { a[1] = now + LOCK_MS; a[0] = 0; }
            return a;
        });
    }
        /** Shared by signup, reset and admin-created accounts. The 100 cap stops absurdly long inputs from slowing the hasher. */
    public static void checkPasswordStrength(String pw) {
        if (pw == null || pw.length() < 8 || pw.length() > 100 || !pw.matches(".*[A-Za-z].*") || !pw.matches(".*\\d.*"))
            throw new BusinessException("Password must be 8-100 characters and contain a letter and a digit");
    }

    /** Max 10 signup attempts per IP per hour (successful or not). */
    public void throttleSignup(String ip) {
        long now = System.currentTimeMillis();
        if (signups.size() > 10_000) signups.clear();
        long[] w = signups.compute(ip, (k, v) -> {
            if (v == null || now - v[1] > 3_600_000L) return new long[]{1, now};
            v[0]++;
            return v;
        });
        if (w[0] > 10) throw new BusinessException("Too many sign-ups from this address - try again later");
    }
        /** Max 20 forgot/reset calls per IP per hour. */
    public void throttleReset(String ip) {
        long now = System.currentTimeMillis();
        if (resetIps.size() > 10_000) resetIps.clear();
        long[] w = resetIps.compute(ip, (k, v) -> {
            if (v == null || now - v[1] > 3_600_000L) return new long[]{1, now};
            v[0]++;
            return v;
        });
        if (w[0] > 20) throw new BusinessException("Too many requests from this address - try again later");
    }

    /** Kill every live session of a user (used after password reset; 4.3 will reuse it for demotions). */
    public void revokeSessions(int userId) { sessions.values().removeIf(s -> s.userId == userId); }

    /**
     * Issue a reset code. Never reveals whether the username exists: always returns normally.
     * A patient who exists but has no login yet gets one created (unusable password) so they can set it via reset.
     */
    public void requestReset(String username) {
        String user = username.trim();
        long now = System.currentTimeMillis();
        Long last = lastReset.get(user);
        if (last != null && now - last < RESET_COOLDOWN_MS) return;     // silent cooldown
        if (lastReset.size() > 10_000) lastReset.clear();
        lastReset.put(user, now);

        String code = String.format("%06d", RNG.nextInt(1_000_000));
        String hash = PasswordHasher.hash(code);        // computed even for unknown users, so timing is the same
        boolean issued = false;
        try (Connection c = Db.get()) {
            c.setAutoCommit(false);
            Integer uid = Sql.one(c, "SELECT UserID FROM USER_ACCOUNT WHERE Username=? AND IsActive=TRUE",
                    rs -> rs.getInt(1), user).orElse(null);
            if (uid == null && !Sql.exists(c, "SELECT 1 FROM USER_ACCOUNT WHERE Username=?", user)) {
                Integer pid = Sql.one(c, "SELECT PatientID FROM PATIENT WHERE PhoneNumber=?",
                        rs -> rs.getInt(1), user).orElse(null);
                if (pid != null)    // '!' can never verify as a password, so the account is unusable until reset
                    uid = Sql.insert(c, "INSERT INTO USER_ACCOUNT (Username, PasswordHash, Role, PatientID) VALUES (?,'!','PATIENT',?)",
                            user, pid);
            }
            if (uid != null) {
                Sql.update(c, "REPLACE INTO PASSWORD_RESET (UserID, CodeHash, ExpiresAt, Attempts) VALUES (?,?,?,0)",
                        uid, hash, now + RESET_TTL_MS);
                issued = true;
            }
            c.commit();
        } catch (SQLException e) {
            throw new BusinessException("Could not process the request - try again");
        }
        if (issued) Notifier.send(user, code);          // after commit, so a code is never sent for a rolled-back row
    }

    /** Verify the code and set the new password. Wrong codes count; the 5th burns the code. */
    public void resetPassword(String username, String code, String newPassword) {
        checkPasswordStrength(newPassword);             // before touching the code, so a weak password doesn't cost an attempt
        String user = username.trim();
        long now = System.currentTimeMillis();
        boolean ok = false;
        Integer uidForRevoke = null;
        try (Connection c = Db.get()) {
            c.setAutoCommit(false);
            Integer uid = Sql.one(c, "SELECT UserID FROM USER_ACCOUNT WHERE Username=? AND IsActive=TRUE",
                    rs -> rs.getInt(1), user).orElse(null);
            Object[] r = uid == null ? null : Sql.one(c,
                    "SELECT CodeHash, ExpiresAt, Attempts FROM PASSWORD_RESET WHERE UserID=? FOR UPDATE",
                    rs -> new Object[]{rs.getString(1), rs.getLong(2), rs.getInt(3)}, uid).orElse(null);

            boolean live = r != null && (long) r[1] >= now;
            boolean match = PasswordHasher.verify(code.trim(), live ? (String) r[0] : DUMMY_HASH);   // always one hash check
            if (live && match) {
                Sql.update(c, "UPDATE USER_ACCOUNT SET PasswordHash=? WHERE UserID=?", PasswordHasher.hash(newPassword), uid);
                Sql.update(c, "DELETE FROM PASSWORD_RESET WHERE UserID=?", uid);
                ok = true;
                uidForRevoke = uid;
            } else if (r != null) {
                int attempts = (int) r[2] + 1;
                if (!live || attempts >= RESET_MAX_ATTEMPTS) Sql.update(c, "DELETE FROM PASSWORD_RESET WHERE UserID=?", uid);
                else Sql.update(c, "UPDATE PASSWORD_RESET SET Attempts=? WHERE UserID=?", attempts, uid);
            }
            c.commit();      // commit also on failure, so the attempt counter / burn actually sticks
        } catch (SQLException e) {
            throw new BusinessException("Could not process the request - try again");
        }
        if (!ok) throw new BusinessException(BAD_CODE);
        fails.remove(user);                              // lift any login lockout
        revokeSessions(uidForRevoke);                    // log out every device
    }
        // ------------------------------------------------------------ admin management (Step 4.3)

        public Table listAdmins() {
            try (Connection c = Db.get()) {
                return Sql.table(c, "SELECT u.UserID AS ID, u.Username, e.EmployeeName AS Name, e.Designation, cl.ClinicName AS Clinic"
                        + " FROM USER_ACCOUNT u LEFT JOIN EMPLOYEE e ON e.EmployeeID=u.EmployeeID"
                        + " LEFT JOIN CLINIC cl ON cl.ClinicID=e.AssignedClinicID"
                        + " WHERE u.Role='ADMIN' AND u.IsActive=TRUE ORDER BY u.UserID");
            } catch (SQLException e) {
                throw new BusinessException("Database error");
            }
        }
    
        /** Make an employee an admin: upgrade their existing account, or create one (username = employee email). */
        public String promote(int employeeId, String initialPassword) {
            checkPasswordStrength(initialPassword);
            String hash = PasswordHasher.hash(initialPassword);
            Integer revoke = null;
            String name, email;
            try (Connection c = Db.get()) {
                c.setAutoCommit(false);
                Object[] e = Sql.one(c, "SELECT EmployeeName, Email FROM EMPLOYEE WHERE EmployeeID=?",
                        rs -> new Object[]{rs.getString(1), rs.getString(2)}, employeeId)
                        .orElseThrow(() -> new BusinessException("Employee not found"));
                name = (String) e[0]; email = (String) e[1];
                Object[] u = Sql.one(c, "SELECT UserID, Role, IsActive FROM USER_ACCOUNT WHERE EmployeeID=? FOR UPDATE",
                        rs -> new Object[]{rs.getInt(1), rs.getString(2), rs.getBoolean(3)}, employeeId).orElse(null);
                if (u == null) {
                    Sql.update(c, "INSERT INTO USER_ACCOUNT (Username, PasswordHash, Role, EmployeeID) VALUES (?,?,'ADMIN',?)",
                            email, hash, employeeId);
                } else {
                    if ("ADMIN".equals(u[1]) && (boolean) u[2]) throw new BusinessException(name + " is already an admin");
                    Sql.update(c, "UPDATE USER_ACCOUNT SET Role='ADMIN', IsActive=TRUE, PasswordHash=? WHERE UserID=?", hash, u[0]);
                    revoke = (Integer) u[0];
                }
                c.commit();
            } catch (SQLException ex) {
                throw new BusinessException(ex.getErrorCode() == 1062
                        ? "That username is already used by another account"
                        : "Could not process the request - try again");
            }
            if (revoke != null) revokeSessions(revoke);      // old receptionist session must not outlive the role change
            return name + " is now an admin (username: " + email + "). Ask them to log in and use 'Forgot password' to set their own.";
        }
    
        /** Remove admin rights. Receptionist -> back to RECEPTION; anyone else (or forceDeactivate) -> account disabled. */
        public String demote(int userId, int actorUserId, boolean forceDeactivate) {
            if (userId == actorUserId) throw new BusinessException("You cannot demote yourself - ask another admin to do it");
            String msg;
            try (Connection c = Db.get()) {
                c.setAutoCommit(false);
                // lock all active admin rows: concurrent demotions are serialised, so the count below is trustworthy
                List<Integer> admins = Sql.query(c,
                        "SELECT UserID FROM USER_ACCOUNT WHERE Role='ADMIN' AND IsActive=TRUE FOR UPDATE", rs -> rs.getInt(1));
                Object[] t = Sql.one(c, "SELECT u.Username, u.Role, u.IsActive, e.Designation, e.EmployeeName"
                        + " FROM USER_ACCOUNT u LEFT JOIN EMPLOYEE e ON e.EmployeeID=u.EmployeeID WHERE u.UserID=?",
                        rs -> new Object[]{rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getString(4), rs.getString(5)}, userId)
                        .orElse(null);
                if (t == null || !"ADMIN".equals(t[1]) || !(boolean) t[2]) throw new BusinessException("That account is not an active admin");
                if (admins.size() <= 1) throw new BusinessException("Cannot demote the last remaining admin");
    
                Sql.update(c, "UPDATE USER_ACCOUNT SET Role='RECEPTION', IsActive=? WHERE UserID=?", !forceDeactivate, userId);
                c.commit();
                String who = t[4] != null ? (String) t[4] : (String) t[0];
                msg = forceDeactivate ? who + " is no longer an admin and the account has been deactivated."
                                      : who + " is no longer an admin. They can still log in with Reception access to their own clinic.";
            } catch (SQLException ex) {
                throw new BusinessException("Could not process the request - try again");
            }
            revokeSessions(userId);                           // a demoted admin must lose access right now, not at cookie expiry
            return msg;
        }
}