-- =====================================================================
-- Dental EMR Appointment Management System
-- Task 1: Database Creation Script (MySQL / MariaDB version)
-- CSE-302: Database Management System (DBMS)
-- =====================================================================

CREATE DATABASE IF NOT EXISTS dental_emr
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE dental_emr;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS PAYMENT;
DROP TABLE IF EXISTS TREATMENT;
DROP TABLE IF EXISTS APPOINTMENT;
DROP TABLE IF EXISTS DOCTOR_SCHEDULE;
DROP TABLE IF EXISTS OPERATORY;
DROP TABLE IF EXISTS EMPLOYEE;
DROP TABLE IF EXISTS PATIENT;
DROP TABLE IF EXISTS DOCTOR;
DROP TABLE IF EXISTS CLINIC;

SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------
-- CLINIC
-- ---------------------------------------------------------------------
CREATE TABLE CLINIC (
    ClinicID        INT AUTO_INCREMENT PRIMARY KEY,
    ClinicName      VARCHAR(100) NOT NULL,
    Address         VARCHAR(255) NOT NULL,
    ContactNumber   VARCHAR(15)  NOT NULL,
    OperatingHours  VARCHAR(50)  NOT NULL,
    ClinicCategory  VARCHAR(30)  NOT NULL
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- DOCTOR
-- ---------------------------------------------------------------------
CREATE TABLE DOCTOR (
    DoctorID        INT AUTO_INCREMENT PRIMARY KEY,
    DoctorName      VARCHAR(100) NOT NULL,
    Specialisation  VARCHAR(60)  NOT NULL,
    Qualification   VARCHAR(60)  NOT NULL,
    ContactNumber   VARCHAR(15)  NOT NULL,
    Email           VARCHAR(100) NOT NULL UNIQUE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- EMPLOYEE  (1:M Clinic -> Employee)
-- ---------------------------------------------------------------------
CREATE TABLE EMPLOYEE (
    EmployeeID        INT AUTO_INCREMENT PRIMARY KEY,
    EmployeeName      VARCHAR(100) NOT NULL,
    Designation       VARCHAR(50)  NOT NULL,
    Salary            DECIMAL(10,2) NOT NULL,
    Email             VARCHAR(100) NOT NULL UNIQUE,
    PhoneNumber       VARCHAR(15)  NOT NULL,
    AssignedClinicID  INT NOT NULL,
    CONSTRAINT fk_employee_clinic FOREIGN KEY (AssignedClinicID)
        REFERENCES CLINIC(ClinicID) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- OPERATORY  (1:M Clinic -> Operatory)
-- ---------------------------------------------------------------------
CREATE TABLE OPERATORY (
    OperatoryID        INT AUTO_INCREMENT PRIMARY KEY,
    OperatoryType      VARCHAR(40) NOT NULL,
    AvailabilityStatus VARCHAR(20) NOT NULL,
    ClinicID           INT NOT NULL,
    CONSTRAINT fk_operatory_clinic FOREIGN KEY (ClinicID)
        REFERENCES CLINIC(ClinicID) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- DOCTOR_SCHEDULE  (associative entity resolving Doctor <-> Clinic M:N)
-- ---------------------------------------------------------------------
CREATE TABLE DOCTOR_SCHEDULE (
    ScheduleID   INT AUTO_INCREMENT PRIMARY KEY,
    DoctorID     INT NOT NULL,
    ClinicID     INT NOT NULL,
    DayOfWeek    VARCHAR(10) NOT NULL,
    StartTime    TIME NOT NULL,
    EndTime      TIME NOT NULL,
    IsAvailable  BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_schedule_doctor FOREIGN KEY (DoctorID)
        REFERENCES DOCTOR(DoctorID) ON DELETE CASCADE,
    CONSTRAINT fk_schedule_clinic FOREIGN KEY (ClinicID)
        REFERENCES CLINIC(ClinicID) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- PATIENT
-- ---------------------------------------------------------------------
CREATE TABLE PATIENT (
    PatientID               INT AUTO_INCREMENT PRIMARY KEY,
    PatientName              VARCHAR(100) NOT NULL,
    Gender                    VARCHAR(10) NOT NULL,
    DateOfBirth               DATE NOT NULL,
    Age                       INT NOT NULL,
    PhoneNumber               VARCHAR(15) NOT NULL UNIQUE,
    Email                     VARCHAR(100),
    Address                   VARCHAR(255) NOT NULL,
    EmergencyContactName      VARCHAR(100),
    EmergencyContactNumber    VARCHAR(15),
    MedicalHistory            TEXT
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- APPOINTMENT  (central fact table)
-- ---------------------------------------------------------------------
CREATE TABLE APPOINTMENT (
    AppointmentID     INT AUTO_INCREMENT PRIMARY KEY,
    AppointmentDate   DATE NOT NULL,
    AppointmentTime   TIME NOT NULL,
    AppointmentType   VARCHAR(20) NOT NULL,
    AppointmentStatus VARCHAR(20) NOT NULL,
    BookingChannel    VARCHAR(20) NOT NULL,
    ChiefComplaint    VARCHAR(255),
    PatientID         INT NOT NULL,
    DoctorID          INT NOT NULL,
    ClinicID          INT NOT NULL,
    OperatoryID       INT NOT NULL,
    CONSTRAINT chk_appointment_type CHECK (AppointmentType IN
        ('In-Clinic','Home Visit','Video Consultation')),
    CONSTRAINT chk_appointment_status CHECK (AppointmentStatus IN
        ('Confirmed','Checked-In','Show','No-Show','Cancelled')),
    CONSTRAINT chk_booking_channel CHECK (BookingChannel IN
        ('Reception','Call Center','Patient Portal')),
    CONSTRAINT fk_appt_patient FOREIGN KEY (PatientID)
        REFERENCES PATIENT(PatientID) ON DELETE RESTRICT,
    CONSTRAINT fk_appt_doctor FOREIGN KEY (DoctorID)
        REFERENCES DOCTOR(DoctorID) ON DELETE RESTRICT,
    CONSTRAINT fk_appt_clinic FOREIGN KEY (ClinicID)
        REFERENCES CLINIC(ClinicID) ON DELETE RESTRICT,
    CONSTRAINT fk_appt_operatory FOREIGN KEY (OperatoryID)
        REFERENCES OPERATORY(OperatoryID) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- TREATMENT  (optional 1:1 with Appointment)
-- ---------------------------------------------------------------------
CREATE TABLE TREATMENT (
    TreatmentID              INT AUTO_INCREMENT PRIMARY KEY,
    AppointmentID            INT NOT NULL UNIQUE,
    TreatmentPerformed        VARCHAR(150) NOT NULL,
    Diagnosis                 VARCHAR(255) NOT NULL,
    ClinicalNotes              TEXT,
    PrescribedMedicines        TEXT,
    FollowUpRecommendations    TEXT,
    TreatmentCost              DECIMAL(10,2) NOT NULL,
    CONSTRAINT fk_treatment_appt FOREIGN KEY (AppointmentID)
        REFERENCES APPOINTMENT(AppointmentID) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- PAYMENT  (optional 1:1 with Appointment)
-- ---------------------------------------------------------------------
CREATE TABLE PAYMENT (
    PaymentID       INT AUTO_INCREMENT PRIMARY KEY,
    AppointmentID   INT NOT NULL UNIQUE,
    Amount          DECIMAL(10,2) NOT NULL,
    PaymentDate     DATE NOT NULL,
    PaymentMode     VARCHAR(20) NOT NULL,
    PaymentStatus   VARCHAR(20) NOT NULL,
    ReceiptNumber   VARCHAR(30) NOT NULL UNIQUE,
    CONSTRAINT chk_payment_mode CHECK (PaymentMode IN
        ('Cash','Card','UPI','Wallet')),
    CONSTRAINT fk_payment_appt FOREIGN KEY (AppointmentID)
        REFERENCES APPOINTMENT(AppointmentID) ON DELETE CASCADE
) ENGINE=InnoDB;
