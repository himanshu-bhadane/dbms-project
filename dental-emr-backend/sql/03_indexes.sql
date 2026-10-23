USE dental_emr;
-- speeds up the searches/reports the backend runs (safe to run once)
CREATE INDEX idx_patient_name   ON PATIENT(PatientName);
CREATE INDEX idx_appt_date      ON APPOINTMENT(AppointmentDate, AppointmentTime);
CREATE INDEX idx_appt_doc_slot  ON APPOINTMENT(DoctorID, AppointmentDate, AppointmentTime);
CREATE INDEX idx_appt_op_slot   ON APPOINTMENT(OperatoryID, AppointmentDate, AppointmentTime);
CREATE INDEX idx_appt_patient   ON APPOINTMENT(PatientID, AppointmentDate);
CREATE INDEX idx_sched_lookup   ON DOCTOR_SCHEDULE(DoctorID, ClinicID, DayOfWeek);
