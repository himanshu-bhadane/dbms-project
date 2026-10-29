package dental.app;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dental.db.Table;
import dental.service.BusinessException;
import dental.service.DentalService;
import dental.auth.AuthException;
import dental.auth.AuthService;
import dental.auth.AuthService.Session;
import dental.auth.ForbiddenException;
import dental.auth.Policy;
import dental.auth.PasswordHasher;

import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/** Thin HTTP/JSON layer over DentalService (JDK built-in server, zero extra deps). Run: dental.app.WebServer */
public final class WebServer {
    interface Fn { Object run(Map<String, String> p) throws Exception; }
    static final DentalService S = new DentalService();
    static final AuthService AUTH = new AuthService();
    static final Map<String, Fn> R = new LinkedHashMap<>();

    static String s(Map<String, String> p, String k) { String v = p.get(k); return v == null || v.isBlank() ? null : v.trim(); }
    static String rq(Map<String, String> p, String k) { String v = s(p, k); if (v == null) throw new BusinessException(k + " is required"); return v; }
    static int i(Map<String, String> p, String k) { return Integer.parseInt(rq(p, k)); }
    static Integer oi(Map<String, String> p, String k) { String v = s(p, k); return v == null ? null : Integer.valueOf(v); }
    static LocalDate d(Map<String, String> p, String k) { return LocalDate.parse(rq(p, k)); }
    static LocalDate od(Map<String, String> p, String k) { String v = s(p, k); return v == null ? null : LocalDate.parse(v); }
    static LocalTime t(Map<String, String> p, String k) { return LocalTime.parse(rq(p, k)); }
    static Map<String, Object> msg(String m) { return Map.of("message", m); }

    static {
        // patients
        R.put("patients", p -> s(p, "id") != null ? S.patientById(i(p, "id")) : s(p, "phone") != null ? S.patientByPhone(rq(p, "phone"))
                : s(p, "name") != null ? S.patientsByName(rq(p, "name")) : S.allPatients());
        R.put("patient-register", p -> msg("Registered, PatientID = " + S.registerPatient(rq(p, "name"), rq(p, "gender"), d(p, "dob"),
                rq(p, "phone"), s(p, "email"), rq(p, "address"), s(p, "ecName"), s(p, "ecNumber"), s(p, "history"))));
        // doctors / roster / clinics
        R.put("doctors", p -> s(p, "clinicId") != null ? S.doctorsOfClinic(i(p, "clinicId")) : S.allDoctors());
        R.put("roster", p -> S.doctorSchedule(i(p, "doctorId")));
        R.put("roster-add", p -> msg("Slot added, ScheduleID = " + S.addSchedule(i(p, "doctorId"), i(p, "clinicId"), rq(p, "day"), t(p, "start"), t(p, "end"))));
        R.put("leave", p -> { S.setScheduleAvailable(i(p, "scheduleId"), "yes".equalsIgnoreCase(rq(p, "available"))); return msg("Roster slot updated"); });
        R.put("clinics", p -> S.allClinics());
        R.put("operatories", p -> S.operatoriesOfClinic(i(p, "clinicId")));
        R.put("employees", p -> S.employeesOfClinic(i(p, "clinicId")));
        // appointments
        R.put("appt-search", p -> S.searchAppointments(oi(p, "patientId"), oi(p, "doctorId"), oi(p, "clinicId"), od(p, "date")));
        R.put("appt-book", p -> {
            int pat = i(p, "patientId"), doc = i(p, "doctorId"), cl = i(p, "clinicId");
            int id = s(p, "operatoryId") == null          // no room given -> the system picks one
                    ? S.bookAuto(pat, doc, cl, d(p, "date"), t(p, "time"), rq(p, "type"), rq(p, "channel"), s(p, "complaint"))
                    : S.book(pat, doc, cl, i(p, "operatoryId"), d(p, "date"), t(p, "time"), rq(p, "type"), rq(p, "channel"), s(p, "complaint"));
            return msg("Booked, AppointmentID = " + id);
        });
        R.put("profile-update", p -> { S.updateProfile(i(p, "id"), s(p, "email"), rq(p, "address")); return msg("Profile updated"); });
        R.put("appt-status", p -> { S.changeStatus(i(p, "id"), rq(p, "status")); return msg("Status changed to " + p.get("status")); });
        R.put("appt-reschedule", p -> { S.reschedule(i(p, "id"), d(p, "date"), t(p, "time")); return msg("Rescheduled"); });
        R.put("appt-complete", p -> msg("Visit completed in one transaction. Receipt: " + S.completeVisit(i(p, "id"), rq(p, "performed"),
                rq(p, "diagnosis"), s(p, "notes"), s(p, "medicines"), s(p, "followUp"), new BigDecimal(rq(p, "cost")), rq(p, "mode"), rq(p, "payStatus"))));
        R.put("appt-settle", p -> { S.settlePayment(i(p, "id")); return msg("Payment settled"); });
        R.put("slots", p -> S.freeSlots(i(p, "doctorId"), i(p, "clinicId"), d(p, "date")));
        R.put("free-rooms", p -> S.freeRooms(i(p, "clinicId"), d(p, "date"), t(p, "time")));
        // reports
        R.put("r-daily", p -> S.dailySchedule(d(p, "date"), oi(p, "clinicId")));
        R.put("r-history", p -> S.patientHistory(i(p, "patientId"), oi(p, "clinicId")));
        R.put("r-revenue", p -> S.clinicRevenue(d(p, "from"), d(p, "to"), oi(p, "clinicId")));
        R.put("r-payments", p -> S.paymentSummary(d(p, "from"), d(p, "to"), oi(p, "clinicId")));
        R.put("r-workload", p -> S.doctorWorkload(d(p, "from"), d(p, "to"), oi(p, "clinicId")));

        // admin management
        R.put("admins", p -> AUTH.listAdmins());
        R.put("admin-promote", p -> msg(AUTH.promote(i(p, "employeeId"), rq(p, "password"))));
        R.put("admin-demote", p -> msg(AUTH.demote(i(p, "userId"), i(p, "actorUserId"), "yes".equalsIgnoreCase(s(p, "deactivate")))));
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        HttpServer srv = HttpServer.create(new InetSocketAddress(port), 0);
        srv.createContext("/api/", WebServer::api);
        srv.createContext("/", ex -> {
            try (InputStream in = WebServer.class.getResourceAsStream("/index.html")) {
                send(ex, 200, "text/html; charset=utf-8", in.readAllBytes());
            }
        });
        srv.start();
        System.out.println("Dental EMR UI -> http://localhost:" + port);
    }

    private static void api(HttpExchange ex) {
        int code = 200; String body;
        try {
            String path = ex.getRequestURI().getPath().substring("/api/".length());
            String raw = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> p = new HashMap<>();
            if (!Set.of("login", "signup", "forgot", "reset", "admin-promote").contains(path)) parse(ex.getRequestURI().getRawQuery(), p);
            parse(raw, p);

            Object result;
            if (path.equals("login")) {
                post(ex);
                Session s = AUTH.login(rq(p, "username"), rq(p, "password"));
                setCookie(ex, s.token, false);
                result = s.toMap();
            } else if (path.equals("signup")) {
                post(ex);
                AUTH.throttleSignup(ex.getRemoteAddress().getAddress().getHostAddress());
                String pw = rq(p, "password");
                AuthService.checkPasswordStrength(pw);
                S.registerPatient(rq(p, "name"), rq(p, "gender"), d(p, "dob"), rq(p, "phone"), s(p, "email"),
                        rq(p, "address"), s(p, "ecName"), s(p, "ecNumber"), s(p, "history"), PasswordHasher.hash(pw));
                Session sess = AUTH.login(rq(p, "phone"), pw);      // auto-login after signup
                setCookie(ex, sess.token, false);
                result = sess.toMap();
            } else if (path.equals("forgot")) {
                post(ex);
                AUTH.throttleReset(ex.getRemoteAddress().getAddress().getHostAddress());
                AUTH.requestReset(rq(p, "username"));
                result = msg("If that account exists, a 6-digit code has been sent. It is valid for 15 minutes.");
            } else if (path.equals("reset")) {
                post(ex);
                AUTH.throttleReset(ex.getRemoteAddress().getAddress().getHostAddress());
                AUTH.resetPassword(rq(p, "username"), rq(p, "code"), rq(p, "password"));
                result = msg("Password changed - please log in with your new password");
            } else if (path.equals("logout")) {
                post(ex);
                AUTH.logout(token(ex));
                setCookie(ex, "", true);
                result = msg("Logged out");
            } else if (path.equals("me")) {
                result = AUTH.require(token(ex)).toMap();
            } else if (path.equals("public-stats")) {
                result = S.publicStats();         // public: aggregates only, no session needed
            } else {
                Session s = AUTH.require(token(ex));     // 401 if not logged in
                Policy.check(s, path, p);                // 403 if not allowed; may pin params to the caller
                Fn fn = R.get(path);
                if (fn == null) throw new BusinessException("Unknown endpoint");
                result = fn.run(p);
            }
            StringBuilder b = new StringBuilder();
            json(b, norm(result));
            body = b.toString();
        } catch (Exception e) {
            code = e instanceof AuthException ? 401 : e instanceof ForbiddenException ? 403 : 400;
            String m = e instanceof BusinessException ? e.getMessage() : "Invalid input: " + e.getMessage();
            StringBuilder b = new StringBuilder("{\"error\":");
            str(b, m); body = b.append('}').toString();
        }
        try { send(ex, code, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8)); } catch (Exception ignored) {}
    }

    private static void post(HttpExchange ex) {
        if (!"POST".equals(ex.getRequestMethod())) throw new BusinessException("POST required");
    }

    /** Reads the SID value from the Cookie header(s); null if absent. */
    private static String token(HttpExchange ex) {
        List<String> hs = ex.getRequestHeaders().get("Cookie");
        if (hs == null) return null;
        for (String h : hs)
            for (String part : h.split(";")) {
                String kv = part.trim();
                if (kv.startsWith("SID=")) return kv.substring(4);
            }
        return null;
    }

    private static void setCookie(HttpExchange ex, String value, boolean clear) {
        // Add "; Secure" once you serve over HTTPS. Without Max-Age it's a session cookie (gone when browser closes).
        ex.getResponseHeaders().add("Set-Cookie",
                "SID=" + value + "; Path=/; HttpOnly; SameSite=Strict" + (clear ? "; Max-Age=0" : ""));
    }

    private static void parse(String q, Map<String, String> out) {
        if (q == null || q.isBlank()) return;
        for (String kv : q.split("&")) {
            int e = kv.indexOf('=');
            if (e > 0) out.put(URLDecoder.decode(kv.substring(0, e), StandardCharsets.UTF_8), URLDecoder.decode(kv.substring(e + 1), StandardCharsets.UTF_8));
        }
    }

    private static void send(HttpExchange ex, int code, String type, byte[] b) throws java.io.IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, b.length);
        try (var os = ex.getResponseBody()) { os.write(b); }
    }

    // ---- result normalisation: everything becomes {cols,rows} or {message} ----
    private static Object norm(Object o) {
        if (o instanceof Optional<?> op) o = op.map(List::of).orElse(List.of());
        if (o instanceof Table t) return Map.of("cols", t.cols(), "rows", t.rows());
        if (o instanceof List<?> l) {
            if (l.isEmpty()) return Map.of("cols", List.of(), "rows", List.of());
            RecordComponent[] rc = l.get(0).getClass().getRecordComponents();
            List<String> cols = new ArrayList<>(); for (RecordComponent c : rc) cols.add(c.getName());
            List<List<String>> rows = new ArrayList<>();
            for (Object r : l) {
                List<String> row = new ArrayList<>();
                try { for (RecordComponent c : rc) { Object v = c.getAccessor().invoke(r); row.add(v == null ? "-" : v.toString()); } }
                catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
                rows.add(row);
            }
            return Map.of("cols", cols, "rows", rows);
        }
        return o;
    }

    private static void json(StringBuilder b, Object o) {
        if (o == null) b.append("null");
        else if (o instanceof Number || o instanceof Boolean) b.append(o);
        else if (o instanceof Map<?, ?> m) {
            b.append('{'); boolean f = true;
            for (Map.Entry<?, ?> e : m.entrySet()) { if (!f) b.append(','); f = false; str(b, String.valueOf(e.getKey())); b.append(':'); json(b, e.getValue()); }
            b.append('}');
        } else if (o instanceof Iterable<?> it) {
            b.append('['); boolean f = true;
            for (Object x : it) { if (!f) b.append(','); f = false; json(b, x); }
            b.append(']');
        } else str(b, o.toString());
    }

    private static void str(StringBuilder b, String s) {
        b.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\""); case '\\' -> b.append("\\\\"); case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r"); case '\t' -> b.append("\\t");
                default -> { if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c); }
            }
        }
        b.append('"');
    }
}
