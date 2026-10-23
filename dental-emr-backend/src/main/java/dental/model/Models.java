package dental.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** One record per relation in the Assignment-3 schema. id = 0 means "not inserted yet". */
public final class Models {
    private Models() {}

    public record Clinic(int id, String name, String address, String contact, String hours, String category) {}
    public record Doctor(int id, String name, String specialisation, String qualification, String contact, String email) {}
    public record Employee(int id, String name, String designation, BigDecimal salary, String email, String phone, int clinicId) {}
    public record Operatory(int id, String type, String status, int clinicId) {}
    public record Schedule(int id, int doctorId, int clinicId, String day, LocalTime start, LocalTime end, boolean available) {}
    public record Patient(int id, String name, String gender, LocalDate dob, int age, String phone, String email,
                          String address, String ecName, String ecNumber, String history) {}
    public record Appointment(int id, LocalDate date, LocalTime time, String type, String status, String channel,
                              String complaint, int patientId, int doctorId, int clinicId, int operatoryId) {}
    public record Treatment(int id, int appointmentId, String performed, String diagnosis, String notes,
                            String medicines, String followUp, BigDecimal cost) {}
    public record Payment(int id, int appointmentId, BigDecimal amount, LocalDate date, String mode,
                          String status, String receipt) {}
}
