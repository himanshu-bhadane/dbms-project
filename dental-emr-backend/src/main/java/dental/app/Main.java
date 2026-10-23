package dental.app;

import dental.service.BusinessException;
import dental.service.DentalService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Scanner;

/** Console front-end over DentalService (swap for REST/Swing later - the service layer doesn't change). */
public final class Main {
    private static final Scanner IN = new Scanner(System.in);
    private static final DentalService S = new DentalService();

    public static void main(String[] args) {
        System.out.println("=== Dental EMR - backend console ===");
        while (true) {
            System.out.println("\n[1] Patients  [2] Appointments  [3] Doctors & schedules  [4] Clinics / rooms / staff  [5] Reports  [0] Exit");
            String ch = ask("> ");
            try {
                switch (ch) {
                    case "1" -> patients();
                    case "2" -> appointments();
                    case "3" -> doctors();
                    case "4" -> clinics();
                    case "5" -> reports();
                    case "0" -> { return; }
                    default -> System.out.println("Pick 0-5");
                }
            } catch (BusinessException e) {
                System.out.println("x " + e.getMessage());
            } catch (RuntimeException e) {   // bad number / date format etc.
                System.out.println("x Invalid input: " + e.getMessage());
            }
        }
    }

    // ---------------------------------------------------------------- menus
    private static void patients() {
        System.out.println("[1] Register [2] By ID [3] By phone [4] Search name [5] Update contact [6] Delete [7] List all");
        switch (ask("> ")) {
            case "1" -> System.out.println("Registered, PatientID = " + S.registerPatient(
                    req("Name: "), req("Gender (Male/Female/Other): "), date("DOB"), req("Phone (10 digits): "),
                    opt("Email"), req("Address: "), opt("Emergency contact name"), opt("Emergency contact number"), opt("Medical history")));
            case "2" -> System.out.println(S.patientById(num("PatientID: ")).map(Object::toString).orElse("Not found"));
            case "3" -> System.out.println(S.patientByPhone(req("Phone: ")).map(Object::toString).orElse("Not found"));
            case "4" -> show(S.patientsByName(req("Name contains: ")));
            case "5" -> { S.updatePatientContact(num("PatientID: "), req("New phone: "), opt("New email"), req("New address: ")); ok(); }
            case "6" -> { S.deletePatient(num("PatientID: ")); ok(); }
            case "7" -> show(S.allPatients());
            default -> System.out.println("?");
        }
    }

    private static void appointments() {
        System.out.println("[1] Book [2] Check-in [3] No-show [4] Cancel [5] Reschedule [6] Complete visit (treatment+payment)"
                + "\n[7] Search [8] Settle pending payment [9] View one");
        switch (ask("> ")) {
            case "1" -> System.out.println("Booked, AppointmentID = " + S.book(num("PatientID: "), num("DoctorID: "), num("ClinicID: "),
                    num("OperatoryID: "), date("Date"), time("Time"), req("Type (In-Clinic/Home Visit/Video Consultation): "),
                    req("Channel (Reception/Call Center/Patient Portal): "), opt("Chief complaint")));
            case "2" -> { S.changeStatus(num("AppointmentID: "), "Checked-In"); ok(); }
            case "3" -> { S.changeStatus(num("AppointmentID: "), "No-Show"); ok(); }
            case "4" -> { S.changeStatus(num("AppointmentID: "), "Cancelled"); ok(); }
            case "5" -> { S.reschedule(num("AppointmentID: "), date("New date"), time("New time")); ok(); }
            case "6" -> System.out.println("Visit completed. Receipt: " + S.completeVisit(num("AppointmentID: "),
                    req("Treatment performed: "), req("Diagnosis: "), opt("Clinical notes"), opt("Prescribed medicines"),
                    opt("Follow-up"), new BigDecimal(req("Cost: ")), req("Mode (Cash/Card/UPI/Wallet): "), req("Status (Paid/Pending): ")));
            case "7" -> S.searchAppointments(optNum("PatientID"), optNum("DoctorID"), optNum("ClinicID"), optDate("Date")).print();
            case "8" -> { S.settlePayment(num("AppointmentID: ")); ok(); }
            case "9" -> {
                int id = num("AppointmentID: ");
                System.out.println(S.appointmentById(id).map(Object::toString).orElse("Not found"));
                S.treatmentOf(id).ifPresent(t -> System.out.println("  " + t));
                S.paymentOf(id).ifPresent(p -> System.out.println("  " + p));
            }
            default -> System.out.println("?");
        }
    }

    private static void doctors() {
        System.out.println("[1] Add doctor [2] List all [3] By clinic [4] Add roster slot [5] View roster [6] Leave on/off slot [7] Delete slot [8] Delete doctor");
        switch (ask("> ")) {
            case "1" -> System.out.println("Added, DoctorID = " + S.addDoctor(req("Name: "), req("Specialisation: "),
                    req("Qualification: "), req("Contact (10 digits): "), req("Email: ")));
            case "2" -> show(S.allDoctors());
            case "3" -> show(S.doctorsOfClinic(num("ClinicID: ")));
            case "4" -> System.out.println("Slot added, ScheduleID = " + S.addSchedule(num("DoctorID: "), num("ClinicID: "),
                    req("Day (Monday..Sunday): "), time("Start"), time("End")));
            case "5" -> S.doctorSchedule(num("DoctorID: ")).print();
            case "6" -> { S.setScheduleAvailable(num("ScheduleID: "), req("Available? (y/n): ").equalsIgnoreCase("y")); ok(); }
            case "7" -> { S.deleteSchedule(num("ScheduleID: ")); ok(); }
            case "8" -> { S.deleteDoctor(num("DoctorID: ")); ok(); }
            default -> System.out.println("?");
        }
    }

    private static void clinics() {
        System.out.println("[1] Add clinic [2] List clinics [3] Add operatory [4] List operatories [5] Operatory status"
                + "\n[6] Add employee [7] List employees [8] Change salary [9] Delete employee");
        switch (ask("> ")) {
            case "1" -> System.out.println("Added, ClinicID = " + S.addClinic(req("Name: "), req("Address: "), req("Contact: "),
                    req("Operating hours: "), req("Category: ")));
            case "2" -> show(S.allClinics());
            case "3" -> System.out.println("Added, OperatoryID = " + S.addOperatory(num("ClinicID: "), req("Type: "), req("Status (Available/Occupied): ")));
            case "4" -> show(S.operatoriesOfClinic(num("ClinicID: ")));
            case "5" -> { S.setOperatoryStatus(num("OperatoryID: "), req("Status (Available/Occupied): ")); ok(); }
            case "6" -> System.out.println("Added, EmployeeID = " + S.addEmployee(req("Name: "), req("Designation: "),
                    new BigDecimal(req("Salary: ")), req("Email: "), req("Phone (10 digits): "), num("ClinicID: ")));
            case "7" -> show(S.employeesOfClinic(num("ClinicID: ")));
            case "8" -> { S.updateEmployeeSalary(num("EmployeeID: "), new BigDecimal(req("New salary: "))); ok(); }
            case "9" -> { S.deleteEmployee(num("EmployeeID: ")); ok(); }
            default -> System.out.println("?");
        }
    }

    private static void reports() {
        System.out.println("[1] Daily schedule [2] Doctor schedule [3] Patient visit history [4] Clinic revenue [5] Payment summary [6] Doctor workload");
        switch (ask("> ")) {
            case "1" -> S.dailySchedule(date("Date"), optNum("ClinicID")).print();
            case "2" -> S.doctorSchedule(num("DoctorID: ")).print();
            case "3" -> S.patientHistory(num("PatientID: ")).print();
            case "4" -> S.clinicRevenue(date("From"), date("To")).print();
            case "5" -> S.paymentSummary(date("From"), date("To")).print();
            case "6" -> S.doctorWorkload(date("From"), date("To")).print();
            default -> System.out.println("?");
        }
    }

    // ---------------------------------------------------------------- input helpers
    private static String ask(String p) { System.out.print(p); return IN.hasNextLine() ? IN.nextLine().trim() : "0"; }
    private static String req(String p) { String s = ask(p); if (s.isEmpty()) throw new BusinessException("Input required"); return s; }
    private static String opt(String p) { String s = ask(p + " (Enter to skip): "); return s.isEmpty() ? null : s; }
    private static int num(String p) { return Integer.parseInt(req(p)); }
    private static Integer optNum(String p) { String s = opt(p); return s == null ? null : Integer.valueOf(s); }
    private static LocalDate date(String p) { return LocalDate.parse(req(p + " [yyyy-mm-dd]: ")); }
    private static LocalDate optDate(String p) { String s = opt(p + " [yyyy-mm-dd]"); return s == null ? null : LocalDate.parse(s); }
    private static LocalTime time(String p) { return LocalTime.parse(req(p + " [HH:mm]: ")); }
    private static void ok() { System.out.println("OK"); }
    private static void show(List<?> l) { if (l.isEmpty()) System.out.println("(none)"); else l.forEach(System.out::println); }
}
