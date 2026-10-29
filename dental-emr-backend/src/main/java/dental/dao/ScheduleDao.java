package dental.dao;

import dental.db.Sql;
import dental.db.Sql.RowMapper;
import dental.model.Models.Schedule;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.List;

public final class ScheduleDao {
    private static final RowMapper<Schedule> M = rs -> new Schedule(
            rs.getInt("ScheduleID"), rs.getInt("DoctorID"), rs.getInt("ClinicID"), rs.getString("DayOfWeek"),
            Sql.time(rs, "StartTime"), Sql.time(rs, "EndTime"), rs.getBoolean("IsAvailable"));

    public int insert(Connection c, Schedule s) throws SQLException {
        return Sql.insert(c, "INSERT INTO DOCTOR_SCHEDULE (DoctorID,ClinicID,DayOfWeek,StartTime,EndTime,IsAvailable) VALUES (?,?,?,?,?,?)",
                s.doctorId(), s.clinicId(), s.day(), s.start(), s.end(), s.available());
    }

    public int setAvailable(Connection c, int scheduleId, boolean available) throws SQLException {
        return Sql.update(c, "UPDATE DOCTOR_SCHEDULE SET IsAvailable=? WHERE ScheduleID=?", available, scheduleId);
    }

    public int delete(Connection c, int scheduleId) throws SQLException {
        return Sql.update(c, "DELETE FROM DOCTOR_SCHEDULE WHERE ScheduleID=?", scheduleId);
    }

    public List<Schedule> byDoctor(Connection c, int doctorId) throws SQLException {
        return Sql.query(c, "SELECT * FROM DOCTOR_SCHEDULE WHERE DoctorID=? "
                + "ORDER BY FIELD(DayOfWeek,'Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday'), StartTime", M, doctorId);
    }

    /** Doctor already has a slot on that weekday overlapping [start,end)? (any clinic - a doctor can't be in two places). */
    public boolean overlaps(Connection c, int doctorId, String day, LocalTime start, LocalTime end) throws SQLException {
        return Sql.exists(c, "SELECT 1 FROM DOCTOR_SCHEDULE WHERE DoctorID=? AND DayOfWeek=? AND StartTime<? AND EndTime>?",
                doctorId, day, end, start);
    }

    /** Is the doctor rostered (and available) at this clinic on this weekday at this time? */
    public boolean covers(Connection c, int doctorId, int clinicId, String day, LocalTime t) throws SQLException {
        return Sql.exists(c, "SELECT 1 FROM DOCTOR_SCHEDULE WHERE DoctorID=? AND ClinicID=? AND DayOfWeek=? "
                + "AND IsAvailable=TRUE AND StartTime<=? AND EndTime>?", doctorId, clinicId, day, t, t);
    }
    /** Available roster windows of a doctor at a clinic on one weekday (used to generate bookable slots). */
    public List<Schedule> forDay(Connection c, int doctorId, int clinicId, String day) throws SQLException {
        return Sql.query(c, "SELECT * FROM DOCTOR_SCHEDULE WHERE DoctorID=? AND ClinicID=? AND DayOfWeek=? "
                + "AND IsAvailable=TRUE ORDER BY StartTime", M, doctorId, clinicId, day);
    }
}
