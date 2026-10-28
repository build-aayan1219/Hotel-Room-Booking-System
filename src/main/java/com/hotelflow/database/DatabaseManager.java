package com.hotelflow.database;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Manages SQLite connection lifecycle, schema initialization, and initial demo data seeding.
 */
public class DatabaseManager {
    private static final String DB_DIR_NAME = ".hotelflow";
    private static final String DB_FILE_NAME = "hotelflow.db";
    private static String dbUrl;

    static {
        // Store in user home directory under .hotelflow/hotelflow.db
        String userHome = System.getProperty("user.home");
        File dir = new File(userHome, DB_DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File dbFile = new File(dir, DB_FILE_NAME);
        dbUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
    }

    public static String getDatabasePath() {
        return dbUrl.replace("jdbc:sqlite:", "");
    }

    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(dbUrl);
        // Enforce SQLite Foreign Keys
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
        return conn;
    }

    /**
     * Initializes the database schema and seeds initial rooms and sample demo records if empty.
     */
    public static void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Rooms table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS rooms (
                    room_number INTEGER PRIMARY KEY,
                    room_type TEXT NOT NULL,
                    nightly_rate_paise INTEGER NOT NULL,
                    floor INTEGER NOT NULL DEFAULT 1,
                    description TEXT,
                    status TEXT NOT NULL DEFAULT 'ACTIVE'
                );
            """);

            // 2. Guests table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS guests (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    full_name TEXT NOT NULL,
                    phone TEXT,
                    email TEXT,
                    address TEXT,
                    created_at TEXT NOT NULL
                );
            """);

            // 3. Bookings table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS bookings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    guest_id INTEGER NOT NULL,
                    room_number INTEGER NOT NULL,
                    check_in_date TEXT NOT NULL,
                    check_out_date TEXT NOT NULL,
                    nightly_rate_paise INTEGER NOT NULL,
                    number_of_nights INTEGER NOT NULL,
                    subtotal_paise INTEGER NOT NULL,
                    tax_amount_paise INTEGER NOT NULL,
                    total_amount_paise INTEGER NOT NULL,
                    status TEXT NOT NULL DEFAULT 'CONFIRMED',
                    created_at TEXT NOT NULL,
                    FOREIGN KEY (guest_id) REFERENCES guests(id) ON DELETE RESTRICT,
                    FOREIGN KEY (room_number) REFERENCES rooms(room_number) ON DELETE RESTRICT
                );
            """);

            // 4. Payments table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS payments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    booking_id INTEGER NOT NULL,
                    amount_paise INTEGER NOT NULL,
                    payment_method TEXT NOT NULL,
                    reference_note TEXT,
                    payment_date TEXT NOT NULL,
                    FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE
                );
            """);

            // Seed initial records if empty
            seedInitialDataIfEmpty(conn);

        } catch (SQLException e) {
            System.err.println("Database initialization error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void seedInitialDataIfEmpty(Connection conn) throws SQLException {
        // Check if rooms table is empty
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM rooms")) {
            if (rs.next() && rs.getInt(1) > 0) {
                return; // already seeded
            }
        }

        System.out.println("Seeding initial HotelFlow demo records...");

        // Insert initial demo rooms (single, double, deluxe, suite)
        String insertRoom = "INSERT INTO rooms (room_number, room_type, nightly_rate_paise, floor, description, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(insertRoom)) {
            // Floor 1: Singles & Doubles
            addRoomBatch(pstmt, 101, "SINGLE", 1500_00L, 1, "Standard Single Bed, City View, High-Speed WiFi", "ACTIVE");
            addRoomBatch(pstmt, 102, "SINGLE", 1500_00L, 1, "Standard Single Bed, Garden Facing, Work Desk", "ACTIVE");
            addRoomBatch(pstmt, 103, "DOUBLE", 2500_00L, 1, "Comfort Double Bed, Balcony, Mini Fridge", "ACTIVE");
            addRoomBatch(pstmt, 104, "DOUBLE", 2500_00L, 1, "Comfort Double Bed, Quiet Courtyard View", "MAINTENANCE");

            // Floor 2: Doubles & Deluxes
            addRoomBatch(pstmt, 201, "DOUBLE", 2500_00L, 2, "Superior Double Bed, Attached Bath, LED Smart TV", "ACTIVE");
            addRoomBatch(pstmt, 202, "DELUXE", 3800_00L, 2, "Deluxe King Bed, Executive Lounge Access, Bathtub", "ACTIVE");
            addRoomBatch(pstmt, 203, "DELUXE", 3800_00L, 2, "Deluxe King Bed, Panoramic View, Coffee Maker", "ACTIVE");

            // Floor 3: Suites
            addRoomBatch(pstmt, 301, "SUITE", 5500_00L, 3, "Presidential Suite with Living Room, Jacuzzi & Dining", "ACTIVE");
            addRoomBatch(pstmt, 302, "SUITE", 5500_00L, 3, "Royal Suite with Master Bedroom, Workspace & Balcony", "ACTIVE");
            pstmt.executeBatch();
        }

        // Insert demo guests
        String insertGuest = "INSERT INTO guests (full_name, phone, email, address, created_at) VALUES (?, ?, ?, ?, ?)";
        int g1Id = 1, g2Id = 2, g3Id = 3;
        try (PreparedStatement pstmt = conn.prepareStatement(insertGuest, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, "Aarav Sharma");
            pstmt.setString(2, "9876543210");
            pstmt.setString(3, "aarav.sharma@example.com");
            pstmt.setString(4, "Koregaon Park, Pune");
            pstmt.setString(5, LocalDateTime.now().minusDays(5).toString());
            pstmt.executeUpdate();

            pstmt.setString(1, "Sneha Kulkarni");
            pstmt.setString(2, "9123456780");
            pstmt.setString(3, "sneha.k@example.com");
            pstmt.setString(4, "Baner, Pune");
            pstmt.setString(5, LocalDateTime.now().minusDays(3).toString());
            pstmt.executeUpdate();

            pstmt.setString(1, "Rohan Verma");
            pstmt.setString(2, "9811223344");
            pstmt.setString(3, "rohan.v@example.com");
            pstmt.setString(4, "Andheri West, Mumbai");
            pstmt.setString(5, LocalDateTime.now().minusDays(2).toString());
            pstmt.executeUpdate();
        }

        LocalDate today = LocalDate.now();

        // Booking 1: Checked-in guest (Currently Occupying Room 202)
        // Arrived yesterday, departs tomorrow (2 nights)
        String insertBooking = """
            INSERT INTO bookings (guest_id, room_number, check_in_date, check_out_date,
                                  nightly_rate_paise, number_of_nights, subtotal_paise,
                                  tax_amount_paise, total_amount_paise, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        int b1Id = 0, b2Id = 0;
        try (PreparedStatement pstmt = conn.prepareStatement(insertBooking, Statement.RETURN_GENERATED_KEYS)) {
            // Booking 1: 2 nights in Deluxe 202 @ 3800 = 7600 subtotal, 912 tax (12%), total 8512
            pstmt.setInt(1, 1);
            pstmt.setInt(2, 202);
            pstmt.setString(3, today.minusDays(1).toString());
            pstmt.setString(4, today.plusDays(1).toString());
            pstmt.setLong(5, 3800_00L);
            pstmt.setInt(6, 2);
            pstmt.setLong(7, 7600_00L);
            pstmt.setLong(8, 912_00L);
            pstmt.setLong(9, 8512_00L);
            pstmt.setString(10, "CHECKED_IN");
            pstmt.setString(11, LocalDateTime.now().minusDays(2).toString());
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) b1Id = rs.getInt(1);
            }

            // Booking 2: Upcoming reservation for today (Room 101, single)
            // Today to 3 days later
            pstmt.setInt(1, 2);
            pstmt.setInt(2, 101);
            pstmt.setString(3, today.toString());
            pstmt.setString(4, today.plusDays(3).toString());
            pstmt.setLong(5, 1500_00L);
            pstmt.setInt(6, 3);
            pstmt.setLong(7, 4500_00L);
            pstmt.setLong(8, 540_00L);
            pstmt.setLong(9, 5040_00L);
            pstmt.setString(10, "CONFIRMED");
            pstmt.setString(11, LocalDateTime.now().minusDays(1).toString());
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) b2Id = rs.getInt(1);
            }
        }

        // Add payments for demo records
        if (b1Id > 0) {
            String insertPay = "INSERT INTO payments (booking_id, amount_paise, payment_method, reference_note, payment_date) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insertPay)) {
                // Booking 1 paid ₹5000 in advance via UPI yesterday, and ₹3512 paid today in cash (total 8512 paid)
                pstmt.setInt(1, b1Id);
                pstmt.setLong(2, 5000_00L);
                pstmt.setString(3, "UPI");
                pstmt.setString(4, "Advance payment via GPay");
                pstmt.setString(5, LocalDateTime.now().minusDays(1).toString());
                pstmt.executeUpdate();

                pstmt.setInt(1, b1Id);
                pstmt.setLong(2, 3512_00L);
                pstmt.setString(3, "CASH");
                pstmt.setString(4, "Check-in balance payment");
                pstmt.setString(5, LocalDateTime.now().toString());
                pstmt.executeUpdate();
            }
        }

        if (b2Id > 0) {
            String insertPay = "INSERT INTO payments (booking_id, amount_paise, payment_method, reference_note, payment_date) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insertPay)) {
                // Booking 2 paid ₹2000 advance deposit today
                pstmt.setInt(1, b2Id);
                pstmt.setLong(2, 2000_00L);
                pstmt.setString(3, "CREDIT_CARD");
                pstmt.setString(4, "Advance booking deposit");
                pstmt.setString(5, LocalDateTime.now().toString());
                pstmt.executeUpdate();
            }
        }
    }

    private static void addRoomBatch(PreparedStatement pstmt, int num, String type, long paise, int floor, String desc, String status) throws SQLException {
        pstmt.setInt(1, num);
        pstmt.setString(2, type);
        pstmt.setLong(3, paise);
        pstmt.setInt(4, floor);
        pstmt.setString(5, desc);
        pstmt.setString(6, status);
        pstmt.addBatch();
    }
}
