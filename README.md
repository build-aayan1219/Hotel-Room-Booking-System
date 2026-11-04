# HotelFlow — Hotel Booking & Management System

**HotelFlow** is a modern hotel management application designed for academic software engineering, OOP vivas, and production demonstration (**VIT Pune**).

It features a **Dual-Mode Execution Architecture**:
1. 📟 **Eclipse Terminal / Console Mode**: Runs directly inside Eclipse's built-in **Console tab** with formatted ASCII tables, interactive menus, printed receipt slips, and zero JavaFX setup needed.
2. 🖥️ **Modern Desktop GUI (JavaFX)**: A desktop interface with a card-style login screen, real-time KPI metrics, revenue line charts, and front-desk workflows.

Both interfaces share **100% of the backend logic, authentication security, and real database layer**.

---

## 🔐 Default Login Credentials

| Role | Username | Password | Access Level |
| :--- | :--- | :--- | :--- |
| **Administrator** | `admin` | `admin123` | Full access (User accounts, Room management, Reports) |
| **Front Desk Staff** | `staff` | `staff123` | Front-desk operations (Reservations, Check-In/Out, Billing) |

---

## 💾 Smart Dual-Engine Database

* **Primary Engine (MySQL)**: Attempts connection to MySQL (`localhost:3306/hotelflow_db`).
* **Automatic Safety Fallback (SQLite)**: If MySQL is not running (e.g., on an evaluator's laptop), the system **automatically falls back to a local SQLite database (`hotelflow.db`)** inside the project folder with zero crashes.
* **Pre-Seeded Data**: Pre-loaded with single/double/deluxe/suite rooms, registered guests, active stays, and sample payments on first launch.

---

## 🏗️ Architecture & Package Structure

```
com.hotelflow
├── Main.java                        # Primary Entry Point (Runs Eclipse Console CLI by default)
├── GuiApp.java                      # JavaFX Desktop GUI Application Launcher
│
├── model/                           # Domain Entities
│   ├── User.java                    # Login entity with password hash & role
│   ├── UserRole.java                # ADMIN vs RECEPTIONIST
│   ├── Room.java                    # Room entity with tariffs, types & statuses
│   ├── Guest.java                   # Guest profile entity
│   ├── Booking.java                 # Booking transaction entity & billing math
│   ├── Payment.java                 # Payment record entity
│   └── Enums: RoomType, RoomStatus, BookingStatus, PaymentStatus
│
├── database/
│   └── DatabaseManager.java         # Smart Dual-Engine: MySQL with SQLite Auto-Fallback
│
├── service/
│   ├── AuthService.java             # User logins, SHA-256 password hashing & sessions
│   ├── HotelService.java            # Conflict overlap math, check-in/out rules, metrics
│   └── HotelConfig.java             # Tax configuration (12% GST)
│
├── cli/                             # 📟 ECLIPSE CONSOLE VERSION (No JavaFX Required)
│   ├── HotelFlowCLI.java            # Interactive command-line UI with numbered menus
│   └── TableFormatter.java          # ASCII box-drawing, dividers & aligned data tables
│
└── ui/                              # 🖥️ DESKTOP JAVAFX GUI
    ├── LoginView.java               # Modern card-based authentication view
    ├── MainView.java                # Sidebar layout with user profile pill & logout
    ├── DashboardView.java           # Live KPI cards, charts & quick action buttons
    ├── RoomManagementView.java      # Room inventory table & modal dialogs
    ├── BookingManagementView.java   # Booking table & 5-step guided wizard
    ├── GuestManagementView.java     # Guest directory table & dialogs
    ├── CheckInOutView.java          # Front desk split check-in / check-out operations
    ├── BillingView.java             # Invoices, payment records & text invoice export
    ├── ReportsView.java             # BarChart, PieChart & room performance metrics
    └── UIUtils.java                 # Badges, alert modals & formatters
```

---

## 🚀 How to Run in Eclipse IDE

### 1. Import into Eclipse
1. Open Eclipse IDE.
2. Click **File** $\rightarrow$ **Import...**
3. Select **Maven** $\rightarrow$ **Existing Maven Projects**, browse to this directory (`Hotel-Room-Booking-System`), and click **Finish**.

### 2. Run in Eclipse Console (Terminal Mode)
1. In the **Project Explorer**, expand: `src/main/java` $\rightarrow$ `com.hotelflow` $\rightarrow$ `Main.java`.
2. **Right-click `Main.java`** $\rightarrow$ **Run As** $\rightarrow$ **Java Application**.
3. The interactive system starts instantly inside the **Console tab** at the bottom of Eclipse!
4. Login with `admin` / `admin123`.

### 3. Run the Desktop GUI (JavaFX Window)
* **Option A**: Right-click `GuiApp.java` in Eclipse $\rightarrow$ **Run As** $\rightarrow$ **Java Application**.
* **Option B**: From the terminal, run:
  ```powershell
  .\mvnw.cmd javafx:run
  ```

---

## 🧪 Automated Unit & Persistence Tests

Run all unit tests:
```powershell
.\mvnw.cmd test
```

All 9 automated test suites verify:
1. `AuthServiceTest`: Admin/staff authentication, invalid password rejection, user registration, and session logout.
2. `HotelBusinessLogicTest`: Strict date validation, 12% GST calculation, and turnaround overlap math.
3. `DatabasePersistenceTest`: Schema seeding, user table initialization, booking insertion, and balance tracking.
