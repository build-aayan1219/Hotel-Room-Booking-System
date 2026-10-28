# HotelFlow — A Java-Based Hotel Booking & Management MVP with Real-Time Dashboard

**HotelFlow** is a modern desktop hotel-management application built using **Java 17+ (Java 25 verified)**, **JavaFX**, **SQLite (JDBC)**, and **Maven**.

Developed as an academic software engineering and Object-Oriented Programming (OOP) project for **VIT Pune**, it elevates a basic terminal room-booking console into a production-grade desktop management suite with live analytics, data persistence, and front-desk workflows.

---

## 🌟 Key Features & Modules

1. **Executive Real-Time Dashboard**:
   - Live KPI cards: Total Rooms, Operationally Available, Currently Occupied, Under Maintenance, Today's Scheduled Check-Ins / Check-Outs, Today's Collected Revenue, and Occupancy Rate.
   - Interactive JavaFX Revenue Trend line chart populated directly from SQLite transactions.
   - Recent reservations & arrivals log with quick action buttons for instant task execution.
   - 30-second silent background refresh cycle (`Timeline`) for live front-desk tracking.

2. **Room Management**:
   - Inventory tracking: Unique Room Number, Type (`SINGLE`, `DOUBLE`, `DELUXE`, `SUITE`), Base Tariff in INR (₹), Floor, and Description.
   - Operational status vs. booking availability: Supports `ACTIVE` and `MAINTENANCE` states.
   - Safe foreign-key deletion protection (prevents deleting rooms with historical bookings to preserve financial audit trail).
   - Instant search and type/status filtering.

3. **Reservations & Booking Management**:
   - 5-step guided reservation wizard with automated date-range conflict prevention.
   - Strict booking overlap condition:
     $$\text{existingCheckIn} < \text{newCheckOut} \land \text{existingCheckOut} > \text{newCheckIn}$$
     *(Allows departure day turnarounds, e.g. guest departs 30 Oct, next guest checks in 30 Oct).*
   - Full lifecycle states: `CONFIRMED` $\rightarrow$ `CHECKED_IN` $\rightarrow$ `CHECKED_OUT` or `CANCELLED`.
   - Comprehensive booking detail modal with full invoice breakdown.

4. **Guest Directory**:
   - Multi-field guest profiles: Full Name, Phone, Email, and Address.
   - One guest can have multiple past and active reservations.
   - Real-time search across names, phone numbers, and emails.

5. **Front Desk Check-In & Check-Out**:
   - Clear operational queues:
     - **Eligible Check-Ins**: Confirmed arrivals ready to be checked in.
     - **Currently In-House**: Checked-in guests occupying rooms.
   - Enforces business rule: *Only checked-in guests count toward live room occupancy; future reservations do not.*
   - Pre-checkout validation with balance warnings to ensure settlement.

6. **Billing, Invoices & Payments**:
   - Itemized bills: Nights $\times$ Nightly Rate = Subtotal, configurable GST (default 12%), Total, Amount Paid, and Balance Due.
   - Stored in SQLite as exact integer paise for mathematical decimal precision.
   - Multi-method transaction logging: UPI, Cash, Credit Card, Debit Card, Net Banking.
   - One-click Text Invoice export (`HotelFlow_Invoice_<id>.txt`) saved to user directory.

7. **Reports & Executive Analytics**:
   - Bar Chart of daily collections for the past 7 days.
   - Pie Chart of reservation status distribution.
   - Room Performance table displaying total bookings, total nights booked, and total revenue contributed per room.

---

## 🏗️ Architecture & OOP Design

The project is structured into clear, maintainable packages:

```
com.hotelflow
├── Main.java                        # JavaFX Application Launcher
├── model/                           # Domain Entities & Enums
│   ├── Room.java                    # Encapsulated Room entity
│   ├── Guest.java                   # Guest profile entity
│   ├── Booking.java                 # Booking transaction entity & pricing math
│   ├── Payment.java                 # Payment entity
│   ├── RoomType.java                # Single, Double, Deluxe, Suite
│   ├── RoomStatus.java              # Active, Maintenance
│   ├── BookingStatus.java           # Confirmed, Checked-In, Checked-Out, Cancelled
│   └── PaymentStatus.java           # Paid, Partially Paid, Unpaid
├── database/
│   └── DatabaseManager.java         # SQLite Connection, DDL, and Seeding
├── dao/                             # Data Access Object Layer (PreparedStatements)
│   ├── RoomDao.java
│   ├── GuestDao.java
│   ├── BookingDao.java
│   └── PaymentDao.java
├── service/                         # Business Logic & Service Layer
│   ├── HotelConfig.java             # Tax configuration (12% GST)
│   └── HotelService.java            # Conflict checking, check-in/out rules, metrics
├── ui/                              # JavaFX Desktop Interface
│   ├── MainView.java                # BorderPane shell with sidebar and top bar
│   ├── DashboardView.java           # KPI cards, charts, and quick actions
│   ├── RoomManagementView.java      # Room inventory table & modal dialogs
│   ├── BookingManagementView.java   # Booking table & 5-step wizard
│   ├── GuestManagementView.java     # Guest directory table & dialogs
│   ├── CheckInOutView.java          # Front desk split operations
│   ├── BillingView.java             # Invoices, payments, and text file export
│   ├── ReportsView.java             # BarChart, PieChart, and room performance
│   └── UIUtils.java                 # Formatters, badges, and modal dialog helpers
└── resources/
    └── css/style.css                # Polished deep-navy and slate CSS styling
```

---

## 💾 Database Location & Schema

SQLite stores records permanently at:
`~/.hotelflow/hotelflow.db` (User home directory)

- Enforces SQLite `PRAGMA foreign_keys = ON;`
- Monetary amounts stored as `INTEGER` (paise) to prevent floating-point rounding errors.
- Pre-seeded with realistic initial rooms (Singles, Doubles, Deluxes, Suites), sample guests, active check-ins, upcoming bookings, and payments on first launch.

---

## 🚀 How to Build and Run

### Prerequisites
- JDK 17 or newer (OpenJDK / JetBrains Runtime 25 verified).
- Maven 3.8+ (A portable `mvnw.bat` wrapper is included in the workspace).

### 1. Run Automated Unit & Persistence Tests
```powershell
.\mvnw.bat test
```

### 2. Launch the Application
```powershell
.\mvnw.bat javafx:run
```
*(Or if standard Maven is in your system PATH: `mvn javafx:run`)*

---

## 🧪 Verification & Viva Demonstration

All core business requirements have been verified with automated JUnit 5 test suites:
1. `HotelBusinessLogicTest`:
   - Validates strict date validation ($checkOut > checkIn$).
   - Validates financial calculations (night counts, 12% GST, totals, partial payment balances).
   - Validates the conflict overlap condition including turnaround date rules.
2. `DatabasePersistenceTest`:
   - Validates automated SQLite table creation (`rooms`, `guests`, `bookings`, `payments`).
   - Validates foreign-key cascades, data insertion, and real-time balance updates.
