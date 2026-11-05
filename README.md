# HotelFlow — Hotel Booking & Management System

**HotelFlow** is a modern hotel management application designed for academic software engineering, OOP vivas, and production demonstration (**VIT Pune**).

It features a **Dual-Mode Execution Architecture**:
1. 📟 **Eclipse Terminal / Console Mode**: Runs directly inside Eclipse's built-in **Console tab** with formatted ASCII tables, interactive menus, printed receipt slips, and zero JavaFX setup needed.
2. 🖥️ **Modern Desktop GUI (JavaFX)**: A desktop interface with a card-style login screen, real-time KPI metrics, revenue line charts, and front-desk workflows.

Both interfaces share **100% of the backend logic, authentication security, Razorpay payment gateway integration, and real database layer**.

---

## 🔐 Default Login Credentials

| Role | Username | Password | Access Level |
| :--- | :--- | :--- | :--- |
| **Administrator** | `admin` | `admin123` | Full access (User accounts, Room management, Reports) |
| **Front Desk Staff** | `staff` | `staff123` | Front-desk operations (Reservations, Check-In/Out, Billing) |

---

## 💳 Razorpay Payment Gateway Integration

HotelFlow is integrated with the **Official Razorpay Java SDK** (`com.razorpay:razorpay-java:1.4.10`) for real online payment processing in **Test Mode**.

### 🌟 Key Features
- **Official Razorpay Standard Checkout**: Complete support for **UPI** (Google Pay, PhonePe, Paytm, QR code), **Credit & Debit Cards** (Visa, Mastercard, RuPay), **Net Banking** (SBI, HDFC, ICICI, Axis), and **Wallets**.
- **Server-Side Cryptographic Verification**: Never trusts frontend success payloads. All payments are verified on the backend using Razorpay's official HMAC-SHA256 signature algorithm (`Utils.verifyPaymentSignature`).
- **Duplicate Payment Protection**: Prevents double charges or replay requests.
- **Dual Flow (Online & Counter)**: Primary one-click Razorpay Checkout, plus offline counter cash recording fallback.
- **Auto-Generated Payment Receipts**: Official computer-generated receipt with Razorpay Order ID, Payment ID, payment channel, and timestamp exportable as `.txt`.
- **Dual-Engine DB Persistence**: Stores `razorpay_order_id`, `razorpay_payment_id`, and `payment_status` (`PENDING`, `SUCCESS`, `FAILED`, `REFUNDED`) across both SQLite and MySQL with automatic migration.

---

## 🔑 How to Setup Razorpay API Keys

To use Razorpay, you need a **Razorpay Key ID** and **Key Secret**.

### 1. Create a Free Razorpay Test Account
1. Go to [https://dashboard.razorpay.com/signup](https://dashboard.razorpay.com/signup) and create a free account.
2. Once signed in, make sure you are in **Test Mode** (toggle on top bar).
3. Navigate to **Account & Settings** $\rightarrow$ **API Keys** $\rightarrow$ **Generate Test Key**.
4. You will receive:
   - **Key ID**: Starts with `rzp_test_...`
   - **Key Secret**: A secret alphanumeric string.

### 2. Configure Credentials (Choose Any Option)

#### Option A: Environment Variables (Recommended)
**Windows PowerShell**:
```powershell
$env:RAZORPAY_KEY_ID="rzp_test_YOUR_KEY_ID"
$env:RAZORPAY_KEY_SECRET="YOUR_KEY_SECRET"
```

**Windows CMD**:
```cmd
set RAZORPAY_KEY_ID=rzp_test_YOUR_KEY_ID
set RAZORPAY_KEY_SECRET=YOUR_KEY_SECRET
```

**Linux / macOS**:
```bash
export RAZORPAY_KEY_ID="rzp_test_YOUR_KEY_ID"
export RAZORPAY_KEY_SECRET="YOUR_KEY_SECRET"
```

#### Option B: Local `config.properties` File
Create a file named `config.properties` in the project root directory (this file is ignored by `.gitignore` so your secrets will never be committed to Git):
```properties
RAZORPAY_KEY_ID=rzp_test_YOUR_KEY_ID
RAZORPAY_KEY_SECRET=YOUR_KEY_SECRET
```
*(You can copy from `config.properties.example`)*.

#### Option C: In-App Prompt
If credentials are not detected when you click **Pay with Razorpay**, HotelFlow will automatically pop up a secure configuration dialog allowing you to enter your Test Key ID and Secret directly for that session!

---

## 🔄 End-to-End Payment Workflow

```
[User Selects Room & Dates]
           ↓
[Backend Computes Tariff + 12% GST]
           ↓
[User clicks "Pay with Razorpay"]
           ↓
[Backend RazorpayService creates Order via Razorpay Java SDK]
           ↓ (Returns Order ID & initializes PENDING Payment record)
[RazorpayHttpBridge hosts Secure Checkout at http://127.0.0.1:18080/checkout]
           ↓
[User chooses UPI / Card / NetBanking & authorizes payment]
           ↓
[Razorpay returns {razorpay_order_id, razorpay_payment_id, razorpay_signature}]
           ↓
[Backend cryptographically verifies HMAC-SHA256 signature]
           ↓
[On Valid Signature: Status updated to SUCCESS, balance cleared, Booking Confirmed]
           ↓
[System displays Success Badge & generates Formal Payment Receipt]
```

---

## 🧪 How to Test Payments in Razorpay Test Mode

When Razorpay Checkout opens, use these official Razorpay test credentials:

| Payment Method | Test Input | Expected Behavior |
| :--- | :--- | :--- |
| **UPI** | `success@razorpay` | Instant payment approval and success |
| **UPI (Failure)** | `failure@razorpay` | Tests payment rejection and failure handling |
| **Credit/Debit Card** | Number: `4111 1111 1111 1111`<br>Expiry: Any future date (e.g. `12/28`)<br>CVV: `123`<br>OTP: `123456` | Card approval, OTP verification, and success |
| **Net Banking** | Choose **SBI**, **HDFC**, or **ICICI** | Razorpay test bank simulator $\rightarrow$ Click **Success** |

---

## 💾 Smart Dual-Engine Database

* **Primary Engine (MySQL)**: Attempts connection to MySQL (`localhost:3306/hotelflow_db`).
* **Automatic Safety Fallback (SQLite)**: If MySQL is not running (e.g., on an evaluator's laptop), the system **automatically falls back to a local SQLite database (`hotelflow.db`)** inside the project folder with zero crashes.
* **Automatic Column Migration**: Automatically detects and migrates existing SQLite/MySQL `payments` tables with `razorpay_order_id`, `razorpay_payment_id`, and `payment_status`.
* **Pre-Seeded Data**: Pre-loaded with single/double/deluxe/suite rooms, registered guests, active stays, and sample payments on first launch.

---

## 🏗️ Architecture & Package Structure

```
com.hotelflow
├── Main.java                        # Primary Entry Point (Console CLI by default)
├── GuiApp.java                      # JavaFX Desktop GUI Application Launcher
│
├── model/                           # Domain Entities
│   ├── User.java                    # Login entity with password hash & role
│   ├── UserRole.java                # ADMIN vs RECEPTIONIST
│   ├── Room.java                    # Room entity with tariffs, types & statuses
│   ├── Guest.java                   # Guest profile entity
│   ├── Booking.java                 # Booking transaction entity & billing math
│   ├── Payment.java                 # Payment entity with Razorpay Order/Payment ID & Status
│   └── Enums: RoomType, RoomStatus, BookingStatus, PaymentStatus (PENDING, SUCCESS, FAILED, REFUNDED)
│
├── database/
│   └── DatabaseManager.java         # Dual-Engine: MySQL with SQLite fallback & auto-migration
│
├── dao/                             # Data Access Object Layer
│   ├── BookingDao.java              # Hydrates balances from SUCCESS payments
│   ├── GuestDao.java                # CRUD for guest profiles
│   ├── RoomDao.java                 # Inventory and tariffs
│   └── PaymentDao.java              # Payment queries, status updates & revenue aggregations
│
├── service/
│   ├── AuthService.java             # User logins, SHA-256 password hashing & sessions
│   ├── HotelService.java            # Conflict overlap math, check-in/out rules, metrics
│   ├── HotelConfig.java             # Tax configuration (12% GST)
│   ├── RazorpayConfig.java          # Safe credential retrieval (Env vars / properties)
│   ├── RazorpayService.java         # Server-side Order creation, HMAC verification & receipts
│   └── RazorpayHttpBridge.java      # Embedded local HTTP bridge for Razorpay Checkout
│
├── cli/                             # 📟 ECLIPSE CONSOLE VERSION (No JavaFX Required)
│   ├── HotelFlowCLI.java            # Interactive command-line UI with Razorpay payment launcher
│   └── TableFormatter.java          # ASCII box-drawing, dividers & aligned data tables
│
└── ui/                              # 🖥️ DESKTOP JAVAFX GUI
    ├── LoginView.java               # Card-based authentication view
    ├── MainView.java                # Sidebar layout with user profile pill & logout
    ├── DashboardView.java           # Live KPI cards, charts & quick action buttons
    ├── RoomManagementView.java      # Room inventory table & modal dialogs
    ├── BookingManagementView.java   # Booking table, 5-step wizard & Razorpay Pay Now
    ├── GuestManagementView.java     # Guest directory table & dialogs
    ├── CheckInOutView.java          # Front desk split check-in / check-out operations
    ├── BillingView.java             # Invoices, payment records & Razorpay Pay Now button
    ├── RazorpayCheckoutDialog.java  # Razorpay Checkout modal, live status & receipt viewer
    ├── ReportsView.java             # BarChart, PieChart & room performance metrics
    └── UIUtils.java                 # Badges, alert modals & formatters
```

---

## 🚀 How to Run the Application

### 1. Run the Desktop GUI (JavaFX Window)
From terminal / PowerShell:
```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-26.0.1"   # Or your JDK path
.\mvnw.cmd javafx:run
```

Or right-click `GuiApp.java` in Eclipse $\rightarrow$ **Run As** $\rightarrow$ **Java Application**.

### 2. Run in Eclipse Console (Terminal Mode)
* **Option A**: In Eclipse Project Explorer $\rightarrow$ Right-click `com.hotelflow.Main.java` $\rightarrow$ **Run As** $\rightarrow$ **Java Application**.
* **Option B**: From terminal:
```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-26.0.1"
.\mvnw.cmd exec:java -Dexec.mainClass="com.hotelflow.Main"
```
Login with `admin` / `admin123`.

---

## 🧪 Automated Unit & Persistence Tests

Run all unit tests:
```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-26.0.1"
.\mvnw.cmd test
```

All **16 automated tests** verify:
1. `AuthServiceTest` (5 tests): Authentication, SHA-256 passwords, role validation.
2. `HotelBusinessLogicTest` (3 tests): Date conflict overlap formulas, 12% GST math, balance tracking.
3. `DatabasePersistenceTest` (1 test): Dual-engine SQLite/MySQL persistence, table seeding.
4. `RazorpayPaymentGatewayTest` (7 tests):
   - Cryptographic HMAC-SHA256 signature verification (Valid signature passes).
   - Forged signature rejection (Invalid signature fails).
   - Tampered payload detection (Altered Order ID or Payment ID fails).
   - Balance isolation (PENDING payments do NOT reduce balance until verified SUCCESS).
   - Duplicate transaction protection (Prevents double capture of the same order/payment).
   - Cancellation and failure handling (Order marked FAILED, balance remains due).
   - Official payment receipt generation with Order ID, Payment ID, and metadata.
