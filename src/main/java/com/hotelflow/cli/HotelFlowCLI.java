package com.hotelflow.cli;

import com.hotelflow.database.DatabaseManager;
import com.hotelflow.model.Booking;
import com.hotelflow.model.BookingStatus;
import com.hotelflow.model.Guest;
import com.hotelflow.model.Payment;
import com.hotelflow.model.Room;
import com.hotelflow.model.RoomStatus;
import com.hotelflow.model.RoomType;
import com.hotelflow.model.User;
import com.hotelflow.model.UserRole;
import com.hotelflow.service.AuthService;
import com.hotelflow.service.HotelConfig;
import com.hotelflow.service.HotelService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Full-featured interactive Terminal / Console Interface for HotelFlow.
 * Runs directly in the Eclipse Console tab with zero JavaFX configuration needed.
 * 100% Standard Java SE.
 */
public class HotelFlowCLI {

    private final HotelService hotelService;
    private final AuthService authService;
    private final Scanner scanner;

    public HotelFlowCLI(HotelService hotelService, AuthService authService) {
        this.hotelService = hotelService;
        this.authService = authService;
        this.scanner = new Scanner(System.in);
    }

    /**
     * Main application loop in terminal.
     */
    public void start() {
        TableFormatter.printHeader("HOTELFLOW — HOTEL MANAGEMENT SYSTEM (CLI)");
        System.out.println("  Connected to: " + DatabaseManager.getActiveEngineName());

        boolean appRunning = true;
        while (appRunning) {
            // Require authentication if not logged in
            if (!authService.isLoggedIn()) {
                boolean authenticated = promptLogin();
                if (!authenticated) {
                    System.out.println("\nExiting HotelFlow CLI. Goodbye!");
                    break;
                }
            }

            // Display Main Menu
            displayMainMenu();
            String choice = readInput("Enter your choice: ").trim();

            switch (choice) {
                case "1" -> handleRoomInventory();
                case "2" -> handleNewReservation();
                case "3" -> handleFrontDeskCheckIn();
                case "4" -> handleFrontDeskCheckOut();
                case "5" -> handleGuestDirectory();
                case "6" -> handleBillingAndInvoices();
                case "7" -> handleExecutiveReports();
                case "8" -> handleUserManagement();
                case "0" -> {
                    authService.logout();
                    TableFormatter.printSuccess("You have been logged out successfully.");
                }
                case "-1", "exit", "quit" -> {
                    appRunning = false;
                    System.out.println("\nThank you for using HotelFlow. Goodbye!");
                }
                default -> TableFormatter.printError("Invalid option! Please enter a number between 0 and 8.");
            }
        }
    }

    // ==================== AUTHENTICATION PROMPT ====================

    private boolean promptLogin() {
        TableFormatter.printSubHeader("🔐 SYSTEM LOGIN REQUIRED");
        System.out.println("  Default Accounts: admin / admin123  OR  staff / staff123");
        TableFormatter.printDivider();

        int attempts = 0;
        while (attempts < 3) {
            String username = readInput("Username: ");
            if (username.equalsIgnoreCase("exit") || username.equalsIgnoreCase("quit")) {
                return false;
            }
            String password = readInput("Password: ");

            try {
                User user = authService.login(username, password);
                TableFormatter.printSuccess("Login successful! Welcome, " + user.getFullName() + " (" + user.getRole().getDisplayName() + ")");
                return true;
            } catch (Exception e) {
                attempts++;
                TableFormatter.printError(e.getMessage() + " (Attempt " + attempts + " of 3)");
            }
        }
        TableFormatter.printError("Too many failed login attempts.");
        return false;
    }

    private void displayMainMenu() {
        User user = authService.getCurrentUser();
        System.out.println();
        TableFormatter.printDivider("=", TableFormatter.DEFAULT_WIDTH);
        System.out.printf("  ACTIVE USER : %-30s | ROLE : %-15s%n", user.getFullName(), user.getRole());
        System.out.printf("  DATABASE    : %-52s%n", DatabaseManager.getActiveEngineName());
        TableFormatter.printDivider("=", TableFormatter.DEFAULT_WIDTH);
        System.out.println("  [1] 🚪 Room Inventory & Management");
        System.out.println("  [2] 📅 Search Availability & Create Reservation");
        System.out.println("  [3] 🛎️  Front Desk: Check-In Arriving Guests");
        System.out.println("  [4] 🚪 Front Desk: Check-Out Departing Guests");
        System.out.println("  [5] 👥 Guest Directory & Profiles");
        System.out.println("  [6] 💳 Billing, Invoices & Payment Recording");
        System.out.println("  [7] 📈 Executive Analytics & Occupancy Dashboard");
        if (authService.isAdmin()) {
            System.out.println("  [8] 👤 User Accounts & Permissions (Admin Only)");
        }
        System.out.println("  [0] 🔒 Logout Current User");
        System.out.println(" [-1] 🛑 Exit System");
        TableFormatter.printDivider("-", TableFormatter.DEFAULT_WIDTH);
    }

    // ==================== 1. ROOM MANAGEMENT ====================

    private void handleRoomInventory() {
        TableFormatter.printSubHeader("🚪 ROOM INVENTORY & TARIFF RATES");
        try {
            List<Room> rooms = hotelService.getAllRooms();
            List<String> headers = List.of("Room #", "Type", "Nightly Rate", "Floor", "Status", "Description");
            List<List<String>> rows = new ArrayList<>();
            for (Room r : rooms) {
                rows.add(List.of(
                        String.valueOf(r.getRoomNumber()),
                        r.getType().getDisplayName(),
                        TableFormatter.formatCurrency(r.getNightlyRate()),
                        String.valueOf(r.getFloor()),
                        r.getStatus().name(),
                        r.getDescription() != null ? r.getDescription() : ""
                ));
            }
            TableFormatter.printTable(headers, rows);

            if (authService.isAdmin()) {
                System.out.println("  [A] Add New Room   [M] Toggle Maintenance Status   [D] Delete Room   [B] Back");
                String opt = readInput("Choose action: ").trim().toUpperCase();
                switch (opt) {
                    case "A" -> addRoomFlow();
                    case "M" -> toggleRoomMaintenance();
                    case "D" -> deleteRoomFlow();
                }
            } else {
                readInput("\nPress Enter to return to main menu...");
            }
        } catch (Exception e) {
            TableFormatter.printError("Failed to fetch rooms: " + e.getMessage());
        }
    }

    private void addRoomFlow() {
        TableFormatter.printSubHeader("Add New Room");
        try {
            int number = readInt("Enter Room Number: ");
            System.out.println("Select Type: [1] SINGLE  [2] DOUBLE  [3] DELUXE  [4] SUITE");
            int typeChoice = readInt("Type: ");
            RoomType type = switch (typeChoice) {
                case 2 -> RoomType.DOUBLE;
                case 3 -> RoomType.DELUXE;
                case 4 -> RoomType.SUITE;
                default -> RoomType.SINGLE;
            };

            BigDecimal rate = readBigDecimal("Enter Nightly Rate (₹): ");
            int floor = readInt("Enter Floor Number: ");
            String desc = readInput("Enter Room Description: ");

            Room newRoom = new Room(number, type, rate, floor, desc, RoomStatus.ACTIVE);
            hotelService.addRoom(newRoom);
            TableFormatter.printSuccess("Room #" + number + " added to inventory successfully!");
        } catch (Exception e) {
            TableFormatter.printError(e.getMessage());
        }
    }

    private void toggleRoomMaintenance() {
        try {
            int number = readInt("Enter Room Number to toggle status: ");
            Room room = hotelService.getAllRooms().stream()
                    .filter(r -> r.getRoomNumber() == number)
                    .findFirst().orElse(null);
            if (room == null) {
                TableFormatter.printError("Room #" + number + " not found.");
                return;
            }
            RoomStatus newStatus = room.getStatus() == RoomStatus.ACTIVE ? RoomStatus.MAINTENANCE : RoomStatus.ACTIVE;
            room.setStatus(newStatus);
            hotelService.updateRoom(room);
            TableFormatter.printSuccess("Room #" + number + " status changed to: " + newStatus);
        } catch (Exception e) {
            TableFormatter.printError(e.getMessage());
        }
    }

    private void deleteRoomFlow() {
        try {
            int number = readInt("Enter Room Number to delete: ");
            hotelService.deleteRoom(number);
            TableFormatter.printSuccess("Room #" + number + " removed from inventory.");
        } catch (Exception e) {
            TableFormatter.printError("Cannot delete room: " + e.getMessage());
        }
    }

    // ==================== 2. RESERVATION ENGINE ====================

    private void handleNewReservation() {
        TableFormatter.printSubHeader("📅 MAKE A NEW RESERVATION");
        try {
            // Step 1: Guest Selection or Quick Registration
            List<Guest> guests = hotelService.getAllGuests();
            Guest selectedGuest = null;
            System.out.println("  [1] Select Existing Registered Guest");
            System.out.println("  [2] Register Walk-In Guest (Fast Track)");
            int gOpt = readInt("Choice: ");

            if (gOpt == 2 || guests.isEmpty()) {
                selectedGuest = registerGuestPrompt();
            } else {
                System.out.println("\nRegistered Guests:");
                for (Guest g : guests) {
                    System.out.printf("  ID %-3d | %-22s | Phone: %s%n", g.getId(), g.getFullName(), g.getPhone());
                }
                int gId = readInt("Enter Guest ID: ");
                selectedGuest = guests.stream().filter(g -> g.getId() == gId).findFirst().orElse(null);
                if (selectedGuest == null) {
                    TableFormatter.printError("Guest ID not found.");
                    return;
                }
            }

            // Step 2: Date Selection
            LocalDate checkIn = readDate("Enter Check-In Date (YYYY-MM-DD, e.g. " + LocalDate.now() + "): ");
            LocalDate checkOut = readDate("Enter Check-Out Date (YYYY-MM-DD, e.g. " + LocalDate.now().plusDays(2) + "): ");
            if (!checkOut.isAfter(checkIn)) {
                TableFormatter.printError("Check-out date must be strictly after check-in date!");
                return;
            }

            // Step 3: Room Type Filter
            System.out.println("\nFilter by Room Type: [0] Any Type  [1] SINGLE  [2] DOUBLE  [3] DELUXE  [4] SUITE");
            int tChoice = readInt("Choice: ");
            RoomType filterType = switch (tChoice) {
                case 1 -> RoomType.SINGLE;
                case 2 -> RoomType.DOUBLE;
                case 3 -> RoomType.DELUXE;
                case 4 -> RoomType.SUITE;
                default -> null;
            };

            // Step 4: Available Rooms Search
            List<Room> available = hotelService.getAvailableRooms(filterType, checkIn, checkOut);
            if (available.isEmpty()) {
                TableFormatter.printWarning("No rooms available for the selected dates and type.");
                return;
            }

            System.out.println("\nAvailable Rooms for " + checkIn + " to " + checkOut + ":");
            List<String> headers = List.of("Room #", "Type", "Nightly Rate (₹)", "Floor", "Features");
            List<List<String>> rows = new ArrayList<>();
            for (Room r : available) {
                rows.add(List.of(
                        String.valueOf(r.getRoomNumber()),
                        r.getType().getDisplayName(),
                        TableFormatter.formatCurrency(r.getNightlyRate()),
                        String.valueOf(r.getFloor()),
                        r.getDescription() != null ? r.getDescription() : ""
                ));
            }
            TableFormatter.printTable(headers, rows);

            int chosenRoomNum = readInt("Choose Room Number: ");
            Room chosenRoom = available.stream().filter(r -> r.getRoomNumber() == chosenRoomNum).findFirst().orElse(null);
            if (chosenRoom == null) {
                TableFormatter.printError("That room number is not in the available list!");
                return;
            }

            // Step 5: Price breakdown and confirmation
            long nights = java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut);
            BigDecimal subtotal = chosenRoom.getNightlyRate().multiply(BigDecimal.valueOf(nights));
            BigDecimal tax = subtotal.multiply(HotelConfig.getTaxRate());
            BigDecimal total = subtotal.add(tax);

            TableFormatter.printSubHeader("Reservation Summary");
            System.out.println("  Guest     : " + selectedGuest.getFullName() + " (" + selectedGuest.getPhone() + ")");
            System.out.println("  Room      : Room #" + chosenRoom.getRoomNumber() + " (" + chosenRoom.getType().getDisplayName() + ")");
            System.out.println("  Stay      : " + checkIn + " to " + checkOut + " (" + nights + " Night(s))");
            System.out.println("  Subtotal  : " + TableFormatter.formatCurrency(subtotal));
            System.out.println("  GST (12%) : " + TableFormatter.formatCurrency(tax));
            System.out.println("  Total Due : " + TableFormatter.formatCurrency(total));
            TableFormatter.printDivider();

            String confirm = readInput("Confirm reservation? (Y/N): ");
            if (confirm.equalsIgnoreCase("Y")) {
                int bookingId = hotelService.createBooking(selectedGuest.getId(), chosenRoom.getRoomNumber(), checkIn, checkOut);
                TableFormatter.printSuccess("Booking #" + bookingId + " CONFIRMED for " + selectedGuest.getFullName() + "!");
            } else {
                System.out.println("Reservation cancelled.");
            }

        } catch (Exception e) {
            TableFormatter.printError("Reservation failed: " + e.getMessage());
        }
    }

    // ==================== 3. FRONT DESK: CHECK-IN ====================

    private void handleFrontDeskCheckIn() {
        TableFormatter.printSubHeader("🛎️  FRONT DESK: ELIGIBLE FOR CHECK-IN (CONFIRMED RESERVATIONS)");
        try {
            List<Booking> confirmed = hotelService.getAllBookings().stream()
                    .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                    .toList();

            if (confirmed.isEmpty()) {
                System.out.println("  No pending arrivals waiting for check-in.");
                return;
            }

            List<String> headers = List.of("Booking #", "Guest Name", "Room #", "Check-In", "Check-Out", "Total Due");
            List<List<String>> rows = new ArrayList<>();
            for (Booking b : confirmed) {
                rows.add(List.of(
                        String.valueOf(b.getId()),
                        b.getGuestName(),
                        String.valueOf(b.getRoomNumber()),
                        b.getCheckInDate().toString(),
                        b.getCheckOutDate().toString(),
                        TableFormatter.formatCurrency(b.getTotalAmount())
                ));
            }
            TableFormatter.printTable(headers, rows);

            int bId = readInt("Enter Booking # to Check-In (or 0 to cancel): ");
            if (bId > 0) {
                hotelService.checkIn(bId);
                TableFormatter.printSuccess("Booking #" + bId + " successfully checked in! Room is now OCCUPIED.");
            }
        } catch (Exception e) {
            TableFormatter.printError(e.getMessage());
        }
    }

    // ==================== 4. FRONT DESK: CHECK-OUT ====================

    private void handleFrontDeskCheckOut() {
        TableFormatter.printSubHeader("🚪 FRONT DESK: CURRENTLY IN-HOUSE (CHECKED-IN GUESTS)");
        try {
            List<Booking> inHouse = hotelService.getAllBookings().stream()
                    .filter(b -> b.getStatus() == BookingStatus.CHECKED_IN)
                    .toList();

            if (inHouse.isEmpty()) {
                System.out.println("  No guests currently occupying rooms.");
                return;
            }

            List<String> headers = List.of("Booking #", "Guest Name", "Room #", "Scheduled Out", "Paid", "Balance Due");
            List<List<String>> rows = new ArrayList<>();
            for (Booking b : inHouse) {
                rows.add(List.of(
                        String.valueOf(b.getId()),
                        b.getGuestName(),
                        String.valueOf(b.getRoomNumber()),
                        b.getCheckOutDate().toString(),
                        TableFormatter.formatCurrency(b.getPaidAmount()),
                        TableFormatter.formatCurrency(b.getBalanceAmount())
                ));
            }
            TableFormatter.printTable(headers, rows);

            int bId = readInt("Enter Booking # to Check-Out (or 0 to cancel): ");
            if (bId > 0) {
                Booking bk = inHouse.stream().filter(b -> b.getId() == bId).findFirst().orElse(null);
                if (bk != null && bk.getBalanceAmount().compareTo(BigDecimal.ZERO) > 0) {
                    TableFormatter.printWarning("Guest has an unpaid balance of " + TableFormatter.formatCurrency(bk.getBalanceAmount()) + "!");
                    String ans = readInput("Proceed with checkout anyway? (Y/N): ");
                    if (!ans.equalsIgnoreCase("Y")) {
                        System.out.println("Checkout aborted. Settle bill in option [6] first.");
                        return;
                    }
                }
                hotelService.checkOut(bId);
                TableFormatter.printSuccess("Booking #" + bId + " checked out. Room is now released!");
            }
        } catch (Exception e) {
            TableFormatter.printError(e.getMessage());
        }
    }

    // ==================== 5. GUEST DIRECTORY ====================

    private void handleGuestDirectory() {
        TableFormatter.printSubHeader("👥 GUEST DIRECTORY & PROFILES");
        try {
            List<Guest> guests = hotelService.getAllGuests();
            List<String> headers = List.of("ID", "Full Name", "Phone", "Email", "Address");
            List<List<String>> rows = new ArrayList<>();
            for (Guest g : guests) {
                rows.add(List.of(
                        String.valueOf(g.getId()),
                        g.getFullName(),
                        g.getPhone() != null ? g.getPhone() : "",
                        g.getEmail() != null ? g.getEmail() : "",
                        g.getAddress() != null ? g.getAddress() : ""
                ));
            }
            TableFormatter.printTable(headers, rows);

            System.out.println("  [A] Register New Guest   [B] Back to Main Menu");
            String opt = readInput("Choice: ").trim().toUpperCase();
            if (opt.equals("A")) {
                registerGuestPrompt();
            }
        } catch (Exception e) {
            TableFormatter.printError(e.getMessage());
        }
    }

    private Guest registerGuestPrompt() {
        TableFormatter.printSubHeader("Register New Guest");
        try {
            String name = readInput("Guest Full Name: ");
            String phone = readInput("Phone Number: ");
            String email = readInput("Email Address: ");
            String addr = readInput("Address / City: ");

            Guest guest = new Guest(name, phone, email, addr);
            int id = hotelService.saveGuest(guest);
            guest.setId(id);
            TableFormatter.printSuccess("Guest '" + name + "' registered with ID #" + id + ".");
            return guest;
        } catch (Exception e) {
            TableFormatter.printError(e.getMessage());
            return null;
        }
    }

    // ==================== 6. BILLING & INVOICES ====================

    private void handleBillingAndInvoices() {
        TableFormatter.printSubHeader("💳 BILLING, INVOICES & PAYMENTS");
        try {
            List<Booking> bookings = hotelService.getAllBookings();
            List<String> headers = List.of("Booking #", "Guest", "Room #", "Total (₹)", "Paid (₹)", "Balance (₹)", "Status");
            List<List<String>> rows = new ArrayList<>();
            for (Booking b : bookings) {
                rows.add(List.of(
                        String.valueOf(b.getId()),
                        b.getGuestName(),
                        String.valueOf(b.getRoomNumber()),
                        TableFormatter.formatCurrency(b.getTotalAmount()),
                        TableFormatter.formatCurrency(b.getPaidAmount()),
                        TableFormatter.formatCurrency(b.getBalanceAmount()),
                        b.getPaymentStatus().name()
                ));
            }
            TableFormatter.printTable(headers, rows);

            System.out.println("  [R] Razorpay Online Checkout   [P] Record Counter Payment   [I] View & Print Invoice   [B] Back");
            String choice = readInput("Action: ").trim().toUpperCase();

            if (choice.equals("R")) {
                int bId = readInt("Enter Booking #: ");
                Booking b = bookings.stream().filter(x -> x.getId() == bId).findFirst().orElse(null);
                if (b == null) {
                    TableFormatter.printError("Booking #" + bId + " not found.");
                    return;
                }
                if (b.getBalanceAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    TableFormatter.printSuccess("Booking is already fully paid!");
                    return;
                }
                System.out.println("Outstanding Balance: " + TableFormatter.formatCurrency(b.getBalanceAmount()));
                BigDecimal amount = readBigDecimal("Payment Amount (₹): ");
                try {
                    com.hotelflow.service.RazorpayService rzpService = new com.hotelflow.service.RazorpayService();
                    var orderDto = rzpService.createOrder(b, amount);
                    com.hotelflow.service.RazorpayHttpBridge bridge = com.hotelflow.service.RazorpayHttpBridge.getInstance(rzpService);
                    String url = bridge.getCheckoutUrl(orderDto.orderId());
                    TableFormatter.printSubHeader("⚡ RAZORPAY SECURE CHECKOUT INITIALIZED");
                    System.out.println("  Order ID : " + orderDto.orderId());
                    System.out.println("  Amount   : " + TableFormatter.formatCurrency(amount));
                    System.out.println("  Gateway  : " + url);
                    System.out.println("  Attempting to open checkout in your default web browser...");
                    try {
                        if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                            java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
                        } else {
                            Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
                        }
                    } catch (Exception ignored) {}
                    readInput("Complete payment in browser and press [Enter] to refresh booking status...");
                } catch (Exception e) {
                    TableFormatter.printError("Razorpay Gateway Error: " + e.getMessage());
                }

            } else if (choice.equals("P")) {
                int bId = readInt("Enter Booking #: ");
                Booking b = bookings.stream().filter(x -> x.getId() == bId).findFirst().orElse(null);
                if (b == null) {
                    TableFormatter.printError("Booking #" + bId + " not found.");
                    return;
                }
                if (b.getBalanceAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    TableFormatter.printSuccess("Booking is already fully paid!");
                    return;
                }

                System.out.println("Outstanding Balance: " + TableFormatter.formatCurrency(b.getBalanceAmount()));
                BigDecimal amount = readBigDecimal("Payment Amount (₹): ");
                System.out.println("Method: [1] UPI   [2] CASH   [3] CREDIT_CARD   [4] DEBIT_CARD   [5] NET_BANKING");
                int mChoice = readInt("Select Method: ");
                String method = switch (mChoice) {
                    case 2 -> "CASH";
                    case 3 -> "CREDIT_CARD";
                    case 4 -> "DEBIT_CARD";
                    case 5 -> "NET_BANKING";
                    default -> "UPI";
                };
                String note = readInput("Reference Note / Txn ID: ");

                hotelService.recordPayment(bId, amount, method, note);
                TableFormatter.printSuccess("Payment of " + TableFormatter.formatCurrency(amount) + " recorded successfully!");

            } else if (choice.equals("I")) {
                int bId = readInt("Enter Booking # for Invoice: ");
                Booking b = bookings.stream().filter(x -> x.getId() == bId).findFirst().orElse(null);
                if (b != null) {
                    printInvoiceSlip(b);
                } else {
                    TableFormatter.printError("Booking not found.");
                }
            }
        } catch (Exception e) {
            TableFormatter.printError(e.getMessage());
        }
    }

    private void printInvoiceSlip(Booking b) {
        System.out.println();
        TableFormatter.printDivider("=", 64);
        TableFormatter.printCentered("HOTELFLOW OFFICIAL TAX INVOICE", 64);
        TableFormatter.printCentered("VIT Pune Academic Software Project", 64);
        TableFormatter.printDivider("=", 64);
        System.out.printf("  Invoice Date : %s%n", LocalDate.now());
        System.out.printf("  Booking ID   : #%d%n", b.getId());
        System.out.printf("  Guest Name   : %s%n", b.getGuestName());
        System.out.printf("  Contact      : %s%n", b.getGuestPhone() != null ? b.getGuestPhone() : "N/A");
        TableFormatter.printDivider("-", 64);
        System.out.printf("  Assigned Room: Room #%d (%s)%n", b.getRoomNumber(), b.getRoomType());
        System.out.printf("  Stay Period  : %s to %s (%d Nights)%n", b.getCheckInDate(), b.getCheckOutDate(), b.getNumberOfNights());
        System.out.printf("  Nightly Rate : %s%n", TableFormatter.formatCurrency(b.getNightlyRate()));
        TableFormatter.printDivider("-", 64);
        System.out.printf("  Subtotal     : %s%n", TableFormatter.formatCurrency(b.getSubtotal()));
        System.out.printf("  GST (12%%)    : %s%n", TableFormatter.formatCurrency(b.getTaxAmount()));
        System.out.printf("  TOTAL AMOUNT : %s%n", TableFormatter.formatCurrency(b.getTotalAmount()));
        TableFormatter.printDivider("-", 64);
        try {
            List<Payment> payments = hotelService.getPaymentsForBooking(b.getId());
            if (payments.isEmpty()) {
                System.out.println("  PAYMENTS     : (No payments recorded yet)");
            } else {
                System.out.println("  PAYMENT TRANSACTIONS:");
                for (Payment p : payments) {
                    System.out.printf("    • %s via %s (Ref: %s)%n",
                            TableFormatter.formatCurrency(p.getAmount()), p.getPaymentMethod(), p.getReferenceNote() + (p.getRazorpayPaymentId() != null ? " [Rzp Txn: " + p.getRazorpayPaymentId() + "]" : ""));
                }
            }
        } catch (Exception ignored) {}
        TableFormatter.printDivider("-", 64);
        System.out.printf("  Amount Paid  : %s%n", TableFormatter.formatCurrency(b.getPaidAmount()));
        System.out.printf("  BALANCE DUE  : %s%n", TableFormatter.formatCurrency(b.getBalanceAmount()));
        System.out.printf("  Status       : %s%n", b.getPaymentStatus());
        TableFormatter.printDivider("=", 64);
        TableFormatter.printCentered("Thank you for choosing HotelFlow!", 64);
        TableFormatter.printDivider("=", 64);
    }

    // ==================== 7. EXECUTIVE REPORTS ====================

    private void handleExecutiveReports() {
        TableFormatter.printSubHeader("📈 EXECUTIVE ANALYTICS & DASHBOARD METRICS");
        try {
            HotelService.DashboardMetrics m = hotelService.getDashboardMetrics();
            System.out.println("  Total Inventory Rooms    : " + m.totalRooms());
            System.out.println("  Operationally Available  : " + m.availableRooms());
            System.out.println("  Currently Occupied       : " + m.occupiedRooms());
            System.out.println("  Under Maintenance        : " + m.maintenanceRooms());
            System.out.printf("  Live Occupancy Rate      : %.1f%%%n", m.occupancyRate());
            TableFormatter.printDivider("-", 50);
            System.out.println("  Today's Scheduled In     : " + m.todayCheckIns());
            System.out.println("  Today's Scheduled Out    : " + m.todayCheckOuts());
            System.out.println("  Today's Collections      : " + TableFormatter.formatCurrency(m.todayRevenue()));
            System.out.println("  Total All-Time Revenue   : " + TableFormatter.formatCurrency(m.totalRevenue()));
            TableFormatter.printDivider("=", 50);

            readInput("\nPress Enter to return to main menu...");
        } catch (Exception e) {
            TableFormatter.printError(e.getMessage());
        }
    }

    // ==================== 8. USER MANAGEMENT ====================

    private void handleUserManagement() {
        if (!authService.isAdmin()) {
            TableFormatter.printError("Access denied! Administrator permission required.");
            return;
        }

        TableFormatter.printSubHeader("👤 USER ACCOUNTS & PERMISSION MANAGEMENT");
        try {
            List<User> users = authService.getAllUsers();
            List<String> headers = List.of("ID", "Username", "Full Name", "Role", "Created At");
            List<List<String>> rows = new ArrayList<>();
            for (User u : users) {
                rows.add(List.of(
                        String.valueOf(u.getId()),
                        u.getUsername(),
                        u.getFullName(),
                        u.getRole().name(),
                        u.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                ));
            }
            TableFormatter.printTable(headers, rows);

            System.out.println("  [A] Add New User   [D] Delete User   [B] Back");
            String opt = readInput("Action: ").trim().toUpperCase();
            if (opt.equals("A")) {
                String uname = readInput("Enter Username: ");
                String pass = readInput("Enter Password (min 4 chars): ");
                String name = readInput("Enter Full Name: ");
                System.out.println("Select Role: [1] RECEPTIONIST   [2] ADMIN");
                int rChoice = readInt("Role: ");
                UserRole role = rChoice == 2 ? UserRole.ADMIN : UserRole.RECEPTIONIST;

                authService.registerUser(uname, pass, name, role);
                TableFormatter.printSuccess("User account '" + uname + "' created as " + role + "!");
            } else if (opt.equals("D")) {
                int uId = readInt("Enter User ID to delete: ");
                authService.deleteUser(uId);
                TableFormatter.printSuccess("User deleted successfully.");
            }
        } catch (Exception e) {
            TableFormatter.printError(e.getMessage());
        }
    }

    // ==================== INPUT UTILITIES ====================

    private String readInput(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    private int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim();
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.println("  [!] Please enter a valid whole number.");
            }
        }
    }

    private BigDecimal readBigDecimal(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim();
            try {
                BigDecimal val = new BigDecimal(line);
                if (val.compareTo(BigDecimal.ZERO) >= 0) return val;
                System.out.println("  [!] Amount must be non-negative.");
            } catch (NumberFormatException e) {
                System.out.println("  [!] Please enter a valid decimal number (e.g. 2500.00).");
            }
        }
    }

    private LocalDate readDate(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim();
            try {
                return LocalDate.parse(line);
            } catch (DateTimeParseException e) {
                System.out.println("  [!] Please use YYYY-MM-DD format (for example: " + LocalDate.now() + ").");
            }
        }
    }
}
