# Dental EMR: Appointment Management System

Java 17 + MySQL 8 + plain JDBC (no frameworks) with a single-page web UI.
Four roles (Admin, Reception, Doctor, Patient) each get their own login and their own view of the data.

- [1. What you need](#1-what-you-need)
- [2. Get the code](#2-get-the-code)
- [3. Set up MySQL](#3-set-up-mysql)
- [4. Create the database](#4-create-the-database)
- [5. Configure the connection](#5-configure-the-connection)
- [6. Create the logins](#6-create-the-logins)
- [7. Run the app](#7-run-the-app)
- [8. Test accounts](#8-test-accounts)
- [9. What to try](#9-what-to-try)
- [10. Resetting everything](#10-resetting-everything)
- [11. Troubleshooting](#11-troubleshooting)
- [12. Project layout](#12-project-layout)


---

## 1. What you need

| Tool | Version | Check with |
|---|---|---|
| JDK | 17 or newer | `java -version` |
| Maven | 3.8 or newer | `mvn -v` |
| MySQL Server | 8.0.16 or newer (8.4 works) | `mysql --version` |
| A browser | any modern one | |

MySQL must be **8.0.16+** because the schema uses `CHECK` constraints, which older versions silently ignore.
You also need internet on first load, because the UI fonts come from Google Fonts. Without it the app still works with fallback fonts.

### Installing the tools

**macOS (Homebrew)**
```bash
brew install openjdk@17 maven mysql
brew services start mysql
```
If `java -version` doesn't show 17, follow the `sudo ln -sfn ...` line that Homebrew prints after installing `openjdk@17`.

**Windows**
1. JDK 17: install Temurin from <https://adoptium.net>, and tick *Set JAVA_HOME* and *Add to PATH*.
2. Maven: download the binary zip from <https://maven.apache.org/download.cgi>, unzip it, and add its `bin` folder to `PATH`.
3. MySQL: install *MySQL Installer* from <https://dev.mysql.com/downloads/installer/>, choose **MySQL Server** and **MySQL Shell/Client**, and remember the root password you set.
4. Add MySQL's `bin` folder (e.g. `C:\Program Files\MySQL\MySQL Server 8.0\bin`) to `PATH` so `mysql` works in the terminal.

**Linux (Debian/Ubuntu)**
```bash
sudo apt update
sudo apt install openjdk-17-jdk maven mysql-server
sudo systemctl start mysql
```

---

## 2. Get the code

```bash
git clone <repo-url>
cd dental-emr-backend
```
(or unzip the folder you were sent and `cd` into it). All commands below are run from this folder, the one containing `pom.xml`.

---

## 3. Set up MySQL

Make sure the server is running, then open a MySQL shell as root:

```bash
mysql -u root -p
```
(On a fresh Homebrew install root has **no password**, so use `mysql -u root`.)

You can use root directly, but a dedicated user is safer. **Optional**, run inside the MySQL shell:

```sql
CREATE USER 'dental'@'localhost' IDENTIFIED BY 'ChooseAPassword1';
GRANT ALL PRIVILEGES ON dental_emr.* TO 'dental'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```
If you do this, use `dental` and its password in [step 5](#5-configure-the-connection), and use `-u dental -p` instead of `-u root -p` in step 4.

---

## 4. Create the database

Run the five SQL files **in this order**. Each command asks for your MySQL password.

```bash
mysql -u root -p < 01_schema_mysql.sql        # database + core tables
mysql -u root -p < 02_sample_data_mysql.sql   # 5 clinics, 25 doctors, 75 employees, 150 patients, 400 appointments
mysql -u root -p < sql/03_indexes.sql         # speeds up searches and reports
mysql -u root -p < sql/04_auth.sql            # USER_ACCOUNT table (logins)
mysql -u root -p < sql/05_password_reset.sql  # PASSWORD_RESET table (forgot-password codes)
```


> **Windows PowerShell** doesn't support `<`. Use **Command Prompt (cmd.exe)** instead, or run this inside the MySQL shell:
> ```sql
> source 01_schema_mysql.sql
> source 02_sample_data_mysql.sql
> source sql/03_indexes.sql
> source sql/04_auth.sql
> source sql/05_password_reset.sql
> ```

**Verify:**
```bash
mysql -u root -p -e "USE dental_emr; SHOW TABLES;"
```
You should see 11 tables: `APPOINTMENT, CLINIC, DOCTOR, DOCTOR_SCHEDULE, EMPLOYEE, OPERATORY, PASSWORD_RESET, PATIENT, PAYMENT, TREATMENT, USER_ACCOUNT`.

---

## 5. Configure the connection

Copy the template and put your MySQL password in it:

```bash
cp src/main/resources/db.properties.example src/main/resources/db.properties
```
On Windows: `copy src\main\resources\db.properties.example src\main\resources\db.properties`.

Edit `src/main/resources/db.properties`:

```properties
url=jdbc:mysql://localhost:3306/dental_emr?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata
user=root
password=YOUR_MYSQL_PASSWORD
```

Don't want a password in a file? Set environment variables instead. They override the file:

```bash
export DB_URL="jdbc:mysql://localhost:3306/dental_emr?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata"
export DB_USER=root
export DB_PASSWORD='YOUR_MYSQL_PASSWORD'
```
(Windows cmd: `set DB_PASSWORD=YOUR_MYSQL_PASSWORD`.)

`db.properties` is git-ignored, so your password is never committed.

---

## 6. Create the logins

The sample data has people but no accounts. This one-off seeder creates a login for every doctor, receptionist and patient, plus one admin:

```bash
mvn compile exec:java -Dexec.mainClass=dental.app.SeedUsers
```

Expected output (it takes a few seconds, since it hashes about 190 passwords):
```
Created: 25 doctors, 11 receptionists, 150 patients, 1 admin
Default passwords -> doctor: Doctor@123 | reception: Recep@123 | patient: Patient@123
```
It is safe to re-run. It skips usernames that already exist, so it prints `0` the second time.

---

## 7. Run the app

```bash
mvn compile exec:java
```
Then open **<http://localhost:8080>**.

- Another port: `mvn compile exec:java -Dexec.args="9090"`
- Stop the server with `Ctrl+C`.
- **After pulling changes to `index.html`**, restart the server and hard-refresh the browser (**Cmd+Shift+R** on Mac, **Ctrl+Shift+R** on Windows/Linux). `mvn compile` copies the page into `target/classes`, and browsers cache aggressively.
- **Keep the terminal visible.** Password-reset codes are printed there (no email or SMS is connected).

---

## 8. Test accounts

| Role | Username | Password | Notes |
|---|---|---|---|
| **Admin** | `jai.kota@instantdental.com` | `Admin@123` | all clinics, staff list, promote/demote admins |
| **Reception** (clinic 1) | `darsh.rau@instantdental.com` | `Recep@123` | sees clinic 1 only |
| **Reception** (clinic 2) | `logan.sami@instantdental.com` | `Recep@123` | sees clinic 2 only |
| **Doctor** | `simon.tata@instantdental.com` | `Doctor@123` | Doctor 1, works at clinics 1 and 4 |
| **Patient** | `9705557720` | `Patient@123` | Patient 1 (Shaurya Sharma) |

- **Usernames:** doctors and staff use their email. **Patients use their 10-digit phone number.**
- Every doctor is `<firstname>.<lastname>@instantdental.com` with the same password, every receptionist uses `Recep@123`, and every patient uses `Patient@123`.
- You can also **create a new patient account** with *Create an account* on the login page.
- These are demo passwords for development only.

### About the sample dates

The sample appointments are dated **July to September 2026**. The *Today* and *My day* screens default to today's date and will look empty. **Change the date picker** to see data, for example:

| Try this | Where | What you'll see |
|---|---|---|
| `2026-09-14` | Reception (clinic 1) → Today | appointments 22 (Checked-In) and 187 (Confirmed) |
| `2026-09-14` | Doctor (Simon Tata) → My day | appointment 187 (Confirmed) |

**Booking new appointments:** pick a future date on a weekday the doctor works at that clinic. For example, Doctor 1 (Simon Tata) works at clinic 1 on **Tuesdays 10:00 to 14:00**, Wednesdays 09:00 to 13:00 and Saturdays 10:00 to 14:00. The booking screens only offer valid times, and the Doctors → Doctor roster card shows each doctor's days.

Your testing **changes the data**, so see [Resetting everything](#10-resetting-everything) if you want a clean slate.

---

## 9. What to try

Sign in as each role and explore. Each role should see **only what it's allowed to**.

### Everyone
- **Landing page** with live counters, the clinic list and the layout on a narrow window.
- **Show/hide password** (eye icon) on every password field.
- **Create an account** (new patient self-signup): duplicate phone, weak password and mismatched confirmation are rejected.
- **Forgot password**: enter a username, read the 6-digit code from the **server terminal**, and set a new password.
  - Wrong code 5 times burns the code. Codes expire after 15 minutes and work once.
  - An unknown username gives the same message and prints no code.
  - A reset logs out that user's other sessions.
- **Log out** and the 30-minute idle timeout.
- 5 wrong passwords lock that username for 5 minutes.

### Reception
- **Today**: pick a date and see status counters. One-click **Check-in / No-show / Cancel** appears only where the workflow allows it.
- **Book appointment (guided)**: find the patient by phone, choose a doctor and date, then only free times and free rooms are offered.
- **Patients**: find, register (age is computed from the date of birth) and view visit history.
- **Overview**: revenue, payments and doctor workload for **your clinic only**.
- **Appointments**: search, reschedule and settle pending payments.
- **Doctors**: roster, add a slot (overlaps are rejected) and leave on/off.

### Admin
- Everything Reception has, but for **all clinics**, with a clinic picker.
- **Clinics → Employees** (salaries are visible to admin only).
- **Admins tab**: list admins, **promote** an employee, **demote** an admin.
  - Demoting removes admin rights only. The person keeps a Reception login at their own clinic.
  - You can't demote yourself or the last remaining admin.
  - A demoted or promoted user's live sessions end immediately.

### Doctor
- **My day**: date picker with prev/next, check-in, no-show.
- **Patient** panel: allergies in red, emergency contact and full history. This only works for patients the doctor has actually had an appointment with.
- **Complete visit**: records treatment and payment and generates a receipt number in **one transaction**.
- **My roster**: own schedule and leave toggles.

### Patient
- **My appointments**: upcoming vs past, with **Reschedule** (pick a date, then a free time) and **Cancel** on Confirmed ones.
- **Book appointment**: the system assigns the room.
- **My history** with receipts.
- **My profile**: click the pencil to edit email or address. The phone number is locked because it is the username.

### Security checks worth attempting
- Log in as a patient, then try `curl` against someone else's data or admin endpoints. You should get **403**.
  ```bash
  curl -c p.txt -d "username=9705557720&password=Patient@123" http://localhost:8080/api/login
  curl -i -b p.txt http://localhost:8080/api/employees        # 403
  curl -i -b p.txt http://localhost:8080/api/r-revenue        # 403
  curl -b p.txt "http://localhost:8080/api/patients?id=2"     # returns patient 1: the ID is pinned to you
  ```
  (On Windows PowerShell use `curl.exe`.)
- A receptionist at clinic 1 asking for clinic 5's reports still only gets clinic 1.
- Try `' OR 1=1 --` in search boxes. All SQL uses bound parameters.
- Open the same free slot in two browsers and book at the same moment. Only one should succeed.

---

## 10. Resetting everything

To go back to a clean database, re-run the five SQL files and the seeder, in the same order as before:

```bash
mysql -u root -p < 01_schema_mysql.sql
mysql -u root -p < 02_sample_data_mysql.sql
mysql -u root -p < sql/03_indexes.sql      # fails with "Duplicate key name" if the indexes already exist: harmless
mysql -u root -p < sql/04_auth.sql
mysql -u root -p < sql/05_password_reset.sql
mvn compile exec:java -Dexec.mainClass=dental.app.SeedUsers
```
Restart the server afterwards, because sessions live in memory.

**Only reset logins** (keep the sample data):
```sql
DELETE FROM USER_ACCOUNT;
```
then run the seeder again.

---

## 11. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `mvn: command not found` / `java: command not found` | Tool not installed or not on `PATH`. Re-check [section 1](#1-what-you-need). |
| `Communications link failure` | MySQL isn't running. Start it (`brew services start mysql`, `sudo systemctl start mysql`, or the Windows *Services* app). |
| `Access denied for user 'root'` | Wrong password in `db.properties` (or `DB_PASSWORD`). |
| `Unknown database 'dental_emr'` | Step 4 wasn't run, or `01_schema_mysql.sql` failed. |
| `Table 'USER_ACCOUNT' doesn't exist` | `sql/04_auth.sql` wasn't run. |
| `Table 'PASSWORD_RESET' doesn't exist` | `sql/05_password_reset.sql` wasn't run. |
| Seeder prints `Created: 0, 0, 0, 0` | Accounts already exist. This is normal on a second run. |
| Seeder fails with a CHECK constraint error mentioning `chk_user_role` | Your `USER_ACCOUNT` table was created from an old `04_auth.sql` that doesn't allow `ADMIN`. Re-run `sql/04_auth.sql` from this repo. |
| `Port 8080 already in use` | Another copy is running. Mac/Linux: `lsof -i :8080` then `kill -9 <PID>`. Or use another port ([step 7](#7-run-the-app)). |
| Login page works but every card says "Session expired" | Open `http://localhost:8080`, not a saved `.html` file. The session cookie needs the server. |
| UI looks old or buttons are missing | Restart the server and hard-refresh the browser. |
| `Unknown endpoint` | An old server process is still running. Stop it and start again. |
| Fonts look like Times/Georgia | No internet, so Google Fonts didn't load. The app still works. |
| PowerShell: `<` is not supported | Use `cmd.exe` or the `source` commands from [step 4](#4-create-the-database). |
| "Today" / "My day" are empty | Sample data is Jul to Sep 2026. Change the date picker. |
| Can't log in as a patient you registered through Reception | Reception-registered patients have no login yet. Use **Forgot password?** with their phone number to set one. |

---

## 12. Project layout

```
dental-emr-backend/
├── pom.xml
├── 01_schema_mysql.sql            core schema
├── 02_sample_data_mysql.sql       sample data
├── sql/
│   ├── 03_indexes.sql             search/report indexes
│   ├── 04_auth.sql                USER_ACCOUNT (logins and roles)
│   └── 05_password_reset.sql      PASSWORD_RESET (reset codes)
└── src/main/
    ├── java/dental/
    │   ├── app/      WebServer (HTTP/JSON layer) · SeedUsers (account seeder) · Main (old console app)
    │   ├── auth/     AuthService (login, sessions, signup, reset, admin management) · Policy (role + ownership rules)
    │   │             PasswordHasher (PBKDF2) · Notifier (prints reset codes) · AuthException · ForbiddenException
    │   ├── service/  DentalService (business rules + transactions) · BusinessException
    │   ├── dao/      one DAO per table + ReportDao (SQL only, no rules)
    │   ├── db/       Db (connections) · Sql (PreparedStatement helper) · Table (report output)
    │   └── model/    Models (one record per table)
    └── resources/
        ├── index.html             the whole web UI (HTML + CSS + JS, no build step)
        └── db.properties.example  connection template
```

**How a request flows:** browser → `WebServer` → session check (`AuthService`) → permission check (`Policy`, default-deny, pins parameters to the caller's own IDs) → `DentalService` (rules, one DB transaction per call) → DAOs → MySQL.

> **Note:** `Main.java` is the original console app. It talks to the service layer directly and **bypasses authentication**. It is kept for reference only.

---

