
package com.hotelflow.database;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Intelligent Database Manager supporting:
 * 1. Primary Engine: MySQL (Client-server RDBMS, e.g. for production or lab MySQL instances)
 * 2. Automatic Fallback: Local SQLite file (hotelflow.db) inside workspace (ensures app NEVER crashes if MySQL is offline)
 * 3. Test Mode: Fast in-memory SQLite (jdbc:sqlite::memory:) for 100% isolated JUnit tests
 */
public class DatabaseManager {

    private static String customDbUrl = null;
    private static boolean isMySqlActive = false;
    private static String activeEngineName = "UNKNOWN";

    // Default MySQL configuration (configurable via System properties or environment)
    private static final String MYSQL_HOST = System.getProperty("hotelflow.mysql.host", "localhost");
    private static final String MYSQL_PORT = System.getProperty("hotelflow.mysql.port", "3306");
    private static final String MYSQL_DB = System.getProperty("hotelflow.mysql.db", "hotelflow_db");
    private static final String MYSQL_USER = System.getProperty("hotelflow.mysql.user", "root");
    private static final String MYSQL_PASS = System.getProperty("hotelflow.mysql.password", "");

    // SQLite local file in workspace directory
    private static final String SQLITE_FILE = "hotelflow.db";

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {}
        determineActiveDatabase();
    }

    /**
     * Attempts MySQL connection; if unavailable, automatically falls back to SQLite.
     */
    public static synchronized void determineActiveDatabase() {
        if (customDbUrl != null) {
            isMySqlActive = customDbUrl.startsWith("jdbc:mysql:");
            activeEngineName = isMySqlActive ? "Custom MySQL" : "Custom SQLite (" + customDbUrl + ")";
            return;
        }

        // Check if user explicitly requested SQLite via JVM property
        String forceEngine = System.getProperty("hotelflow.db.engine", "auto");
        if ("sqlite".equalsIgnoreCase(forceEngine)) {
            useSqliteFallback("Configured via system property");
            return;
        }

        // Try connecting to MySQL
        String mysqlUrl = "jdbc:mysql://" + MYSQL_HOST + ":" + MYSQL_PORT + "/" + MYSQL_DB 
                + "?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=1500&socketTimeout=2000";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            DriverManager.setLoginTimeout(2);
            try (Connection conn = DriverManager.getConnection(mysqlUrl, MYSQL_USER, MYSQL_PASS)) {
                isMySqlActive = true;
                activeEngineName = "MySQL (" + MYSQL_HOST + ":" + MYSQL_PORT + "/" + MYSQL_DB + ")";
                System.out.println("[Database] Connected successfully to " + activeEngineName);
                return;
            }
        } catch (Throwable t) {
            // MySQL unavailable - switch seamlessly to SQLite
            useSqliteFallback("MySQL server offline or unreachable (" + t.getClass().getSimpleName() + ")");
        }
    }

    private static void useSqliteFallback(String reason) {
        isMySqlActive = false;
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {}
        File dbFile = new File(SQLITE_FILE);
        activeEngineName = "SQLite (Local File: " + dbFile.getAbsolutePath() + ")";
        System.out.println("[Database] " + reason + ". Seamlessly using " + activeEngineName);
    }

    /**
     * Override database URL (useful for in-memory SQLite unit tests).
     */
    public static synchronized void setCustomDbUrl(String url) {
        customDbUrl = url;
        determineActiveDatabase();
    }

    public static synchronized void resetCustomDbUrl() {
        customDbUrl = null;
        determineActiveDatabase();
    }

    public static boolean isMySql() {
        return isMySqlActive;
    }

    public static String getActiveEngineName() {
        return activeEngineName;
    }

    /**
     * Obtains a new database connection.
     */
    public static Connection getConnection() throws SQLException {
        if (customDbUrl != null) {
            if (!customDbUrl.startsWith("jdbc:mysql:")) {
                try {
                    Class.forName("org.sqlite.JDBC");
                } catch (ClassNotFoundException ignored) {}
            }
            Connection conn = DriverManager.getConnection(customDbUrl);
            if (!customDbUrl.startsWith("jdbc:mysql:")) {
                enableSqliteForeignKeys(conn);
            }
            return conn;
        }

        if (isMySqlActive) {
            String mysqlUrl = "jdbc:mysql://" + MYSQL_HOST + ":" + MYSQL_PORT + "/" + MYSQL_DB 
                    + "?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true";
            return DriverManager.getConnection(mysqlUrl, MYSQL_USER, MYSQL_PASS);
        } else {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException ignored) {}
            File dbFile = new File(SQLITE_FILE);
            String sqliteUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            Connection conn = DriverManager.getConnection(sqliteUrl);
            enableSqliteForeignKeys(conn);
            return conn;
        }
    }

    private static void enableSqliteForeignKeys(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        } catch (SQLException ignored) {}
    }

    /**
     * Initializes tables and seeds initial demo data (rooms, guests, bookings, payments, and users).
     */
    public static void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            if (isMySqlActive) {
                initMySqlSchema(stmt);
            } else {
                initSqliteSchema(stmt);
            }

            // Apply migrations for Razorpay fields on existing databases
            migratePaymentsTableIfNecessary(conn);

            // Seed initial records if empty
            seedInitialDataIfEmpty(conn);

        } catch (SQLException e) {
            System.err.println("[Database] Initialization error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void initSqliteSchema(Statement stmt) throws SQLException {
        // 1. Users table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                full_name TEXT NOT NULL,
                role TEXT NOT NULL,
                created_at TEXT NOT NULL
            );
        """);

        // 2. Rooms table
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

        // 3. Guests table
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

        // 4. Bookings table
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

        // 5. Payments table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS payments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                booking_id INTEGER NOT NULL,
                amount_paise INTEGER NOT NULL,
                payment_method TEXT NOT NULL,
                reference_note TEXT,
                payment_date TEXT NOT NULL,
                razorpay_order_id TEXT,
                razorpay_payment_id TEXT,
                payment_status TEXT NOT NULL DEFAULT 'SUCCESS',
                FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE
            );
        """);
    }

    private static void initMySqlSchema(Statement stmt) throws SQLException {
        // 1. Users table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id INT PRIMARY KEY AUTO_INCREMENT,
                username VARCHAR(50) NOT NULL UNIQUE,
                password_hash VARCHAR(100) NOT NULL,
                full_name VARCHAR(100) NOT NULL,
                role VARCHAR(30) NOT NULL,
                created_at VARCHAR(50) NOT NULL
            );
        """);

        // 2. Rooms table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS rooms (
                room_number INT PRIMARY KEY,
                room_type VARCHAR(50) NOT NULL,
                nightly_rate_paise BIGINT NOT NULL,
                floor INT NOT NULL DEFAULT 1,
                description TEXT,
                status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
            );
        """);

        // 3. Guests table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS guests (
                id INT PRIMARY KEY AUTO_INCREMENT,
                full_name VARCHAR(150) NOT NULL,
                phone VARCHAR(50),
                email VARCHAR(100),
                address VARCHAR(255),
                created_at VARCHAR(50) NOT NULL
            );
        """);

        // 4. Bookings table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS bookings (
                id INT PRIMARY KEY AUTO_INCREMENT,
                guest_id INT NOT NULL,
                room_number INT NOT NULL,
                check_in_date VARCHAR(20) NOT NULL,
                check_out_date VARCHAR(20) NOT NULL,
                nightly_rate_paise BIGINT NOT NULL,
                number_of_nights INT NOT NULL,
                subtotal_paise BIGINT NOT NULL,
                tax_amount_paise BIGINT NOT NULL,
                total_amount_paise BIGINT NOT NULL,
                status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED',
                created_at VARCHAR(50) NOT NULL,
                FOREIGN KEY (guest_id) REFERENCES guests(id) ON DELETE RESTRICT,
                FOREIGN KEY (room_number) REFERENCES rooms(room_number) ON DELETE RESTRICT
            );
        """);

        // 5. Payments table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS payments (
                id INT PRIMARY KEY AUTO_INCREMENT,
                booking_id INT NOT NULL,
                amount_paise BIGINT NOT NULL,
                payment_method VARCHAR(50) NOT NULL,
                reference_note VARCHAR(255),
                payment_date VARCHAR(50) NOT NULL,
                razorpay_order_id VARCHAR(100),
                razorpay_payment_id VARCHAR(100),
                payment_status VARCHAR(30) NOT NULL DEFAULT 'SUCCESS',
                FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE
            );
        """);
    }

    private static void seedInitialDataIfEmpty(Connection conn) throws SQLException {
        // 1. Seed users if empty
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
            if (rs.next() && rs.getInt(1) == 0) {
                System.out.println("[Database] Seeding default authentication user accounts...");
                String insertUser = "INSERT INTO users (username, password_hash, full_name, role, created_at) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(insertUser)) {
                    // Admin: admin / admin123
                    pstmt.setString(1, "admin");
                    pstmt.setString(2, hashPassword("admin123"));
                    pstmt.setString(3, "Hotel Administrator");
                    pstmt.setString(4, "ADMIN");
                    pstmt.setString(5, LocalDateTime.now().toString());
                    pstmt.executeUpdate();

                    // Staff: staff / staff123
                    pstmt.setString(1, "staff");
                    pstmt.setString(2, hashPassword("staff123"));
                    pstmt.setString(3, "Front Desk Receptionist");
                    pstmt.setString(4, "RECEPTIONIST");
                    pstmt.setString(5, LocalDateTime.now().toString());
                    pstmt.executeUpdate();
                }
            }
        }

        // 2. Seed rooms if empty
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM rooms")) {
            if (rs.next() && rs.getInt(1) > 0) {
                return; // Rooms already seeded
            }
        }

        System.out.println("[Database] Seeding initial HotelFlow demo records...");

        // Insert initial demo rooms
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
        try (PreparedStatement pstmt = conn.prepareStatement(insertGuest)) {
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

        // Insert demo bookings
        String insertBooking = """
            INSERT INTO bookings (guest_id, room_number, check_in_date, check_out_date,
                                  nightly_rate_paise, number_of_nights, subtotal_paise,
                                  tax_amount_paise, total_amount_paise, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        int b1Id = 0, b2Id = 0;
        try (PreparedStatement pstmt = conn.prepareStatement(insertBooking, Statement.RETURN_GENERATED_KEYS)) {
            // Booking 1: Checked-in guest in Room 202
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

            // Booking 2: Upcoming reservation in Room 101
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

        // Insert demo payments
        if (b1Id > 0) {
            String insertPay = "INSERT INTO payments (booking_id, amount_paise, payment_method, reference_note, payment_date) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insertPay)) {
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

    /**
     * SHA-256 password hashing helper.
     */
    public static String hashPassword(String password) {
        if (password == null) password = "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String hexByte = Integer.toHexString(0xff & b);
                if (hexByte.length() == 1) hex.append('0');
                hex.append(hexByte);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 error: " + e.getMessage(), e);
        }
    }

    private static void migratePaymentsTableIfNecessary(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            if (isMySqlActive) {
                tryAddMySqlColumn(stmt, "payments", "razorpay_order_id", "VARCHAR(100)");
                tryAddMySqlColumn(stmt, "payments", "razorpay_payment_id", "VARCHAR(100)");
                tryAddMySqlColumn(stmt, "payments", "payment_status", "VARCHAR(30) NOT NULL DEFAULT 'SUCCESS'");
            } else {
                tryAddSqliteColumn(stmt, "payments", "razorpay_order_id", "TEXT");
                tryAddSqliteColumn(stmt, "payments", "razorpay_payment_id", "TEXT");
                tryAddSqliteColumn(stmt, "payments", "payment_status", "TEXT NOT NULL DEFAULT 'SUCCESS'");
            }
        } catch (Exception e) {
            System.err.println("[Database] Migration check warning: " + e.getMessage());
        }
    }

    private static void tryAddSqliteColumn(Statement stmt, String table, String column, String colDef) {
        try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            boolean found = false;
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + colDef);
                System.out.println("[Database] SQLite migrated table " + table + ": added column " + column);
            }
        } catch (SQLException ignored) {}
    }

    private static void tryAddMySqlColumn(Statement stmt, String table, String column, String colDef) {
        try {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + colDef);
            System.out.println("[Database] MySQL migrated table " + table + ": added column " + column);
        } catch (SQLException ignored) {}
    }
}