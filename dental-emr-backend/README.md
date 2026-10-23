# Dental EMR - JDBC Backend (CSE-302)

Java 17 + MySQL 8 + plain JDBC (Connector/J). No frameworks.

## Run
```bash
# 1. DB (from your existing files, in this order)
mysql -u root -p < 01_schema_mysql.sql
mysql -u root -p < 02_sample_data_mysql.sql
mysql -u root -p < sql/03_indexes.sql        # optional, speeds up search/reports

# 2. credentials: edit src/main/resources/db.properties  (or set DB_URL / DB_USER / DB_PASSWORD)

# 3. run
mvn compile exec:java
```

## Layout
```
dental.db       Db (connections) · Sql (PreparedStatement helper) · Table (report output)
dental.model    Models  - one record per relation (Clinic, Doctor, Patient, ...)
dental.dao      one DAO per table + ReportDao (joined searches & reports). SQL only, no rules.
dental.service  DentalService - business rules + transactions. BusinessException = user-safe errors.
dental.app      Main - console menu (replaceable by REST/Swing; service API stays the same)
```

## Rules enforced in code (beyond the schema)
| Area | Rule |
|---|---|
| Patient | phone 10 digits + unique (pre-check + DB UNIQUE); **Age computed from DOB**, never typed |
| Booking | patient/doctor/clinic/operatory exist; operatory must belong to the chosen clinic; no past dates |
| Booking | doctor must be rostered **and IsAvailable** at that clinic on that weekday/time (DOCTOR_SCHEDULE) |
| Booking | no double-booking of doctor, operatory or patient at same date+time; Cancelled frees the slot |
| Concurrency | doctor + operatory rows `SELECT ... FOR UPDATE` before checks, so 2 receptionists can't take one slot |
| Status | Confirmed -> Checked-In / No-Show / Cancelled ; Checked-In -> Show / Cancelled ; terminal otherwise |
| Visit | `completeVisit` = one transaction: status->Show + TREATMENT + PAYMENT + receipt `RCPT<year><id>`; rollback on any failure |
| Roster | end > start; no overlapping slots for one doctor on a weekday; leave = toggle slot IsAvailable |
| Errors | MySQL 1062/1451/1452/3819 translated to readable messages |

## Spec coverage (Assignment 1)
- Search patients: by ID, phone, name (LIKE) · search appointments: by patient/doctor/clinic/date (any combo)
- Reports: daily schedule, doctor schedule, patient visit history, clinic-wise revenue, payment summary (+ doctor workload)
- CRUD: patient, doctor, schedule, clinic, operatory, employee, appointment lifecycle

## Known data caveats
- `02_sample_data_mysql.sql` is random: many appointments fall outside the doctor roster, so *rescheduling those*
  will fail the roster check. New bookings should use a doctor/clinic/day from `Doctor schedule`.
- Operatory.AvailabilityStatus is a live flag; booking availability is checked per time-slot, not by that flag.

## Web UI
```bash
mvn compile exec:java -Dexec.mainClass=dental.app.WebServer      # then open http://localhost:8080
```
Tabs: Overview (revenue / payments / workload), Patients, Appointments, Doctors, Clinics. Each card names the backend rule it demonstrates;
rule violations come back from `DentalService` and are shown in red.
