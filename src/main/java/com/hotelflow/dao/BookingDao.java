package com.hotelflow.dao;

import com.hotelflow.database.DatabaseManager;
import com.hotelflow.model.Booking;
import com.hotelflow.model.BookingStatus;
import com.hotelflow.model.RoomType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BookingDao {

    public static long toPaise(BigDecimal amount) {
        if (amount == null) return 0L;
        return amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static BigDecimal fromPaise(long paise) {
        return BigDecimal.valueOf(paise).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public int insert(Booking booking) throws SQLException {
        String sql = """
            INSERT INTO bookings (guest_id, room_number, check_in_date, check_out_date,
                                  nightly_rate_paise, number_of_nights, subtotal_paise,
                                  tax_amount_paise, total_amount_paise, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, booking.getGuestId());
            pstmt.setInt(2, booking.getRoomNumber());
            pstmt.setString(3, booking.getCheckInDate().toString());
            pstmt.setString(4, booking.getCheckOutDate().toString());
            pstmt.setLong(5, toPaise(booking.getNightlyRate()));
            pstmt.setInt(6, booking.getNumberOfNights());
            pstmt.setLong(7, toPaise(booking.getSubtotal()));
            pstmt.setLong(8, toPaise(booking.getTaxAmount()));
            pstmt.setLong(9, toPaise(booking.getTotalAmount()));
            pstmt.setString(10, booking.getStatus().name());
            pstmt.setString(11, booking.getCreatedAt() != null ? booking.getCreatedAt().toString() : LocalDateTime.now().toString());
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    booking.setId(id);
                    return id;
                }
            }
        }
        return 0;
    }

    public void updateStatus(int bookingId, BookingStatus newStatus) throws SQLException {
        String sql = "UPDATE bookings SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newStatus.name());
            pstmt.setInt(2, bookingId);
            pstmt.executeUpdate();
        }
    }

    public Optional<Booking> findById(int id) throws SQLException {
        String sql = """
            SELECT b.*, g.full_name as guest_name, g.phone as guest_phone, r.room_type,
                   COALESCE((SELECT SUM(amount_paise) FROM payments p WHERE p.booking_id = b.id), 0) as paid_paise
            FROM bookings b
            JOIN guests g ON b.guest_id = g.id
            JOIN rooms r ON b.room_number = r.room_number
            WHERE b.id = ?
        """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<Booking> findAll() throws SQLException {
        List<Booking> list = new ArrayList<>();
        String sql = """
            SELECT b.*, g.full_name as guest_name, g.phone as guest_phone, r.room_type,
                   COALESCE((SELECT SUM(amount_paise) FROM payments p WHERE p.booking_id = b.id), 0) as paid_paise
            FROM bookings b
            JOIN guests g ON b.guest_id = g.id
            JOIN rooms r ON b.room_number = r.room_number
            ORDER BY b.id DESC
        """;
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    /**
     * Checks if a room has any overlapping non-cancelled bookings.
     * Condition: existingCheckIn < newCheckOut && existingCheckOut > newCheckIn
     */
    public boolean hasConflict(int roomNumber, LocalDate newCheckIn, LocalDate newCheckOut, int excludeBookingId) throws SQLException {
        String sql = """
            SELECT COUNT(*) FROM bookings
            WHERE room_number = ?
              AND status != 'CANCELLED'
              AND id != ?
              AND check_in_date < ?
              AND check_out_date > ?
        """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, roomNumber);
            pstmt.setInt(2, excludeBookingId);
            pstmt.setString(3, newCheckOut.toString());
            pstmt.setString(4, newCheckIn.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    /**
     * Finds room numbers that are already reserved/occupied in the requested date range.
     */
    public List<Integer> getConflictingRoomNumbers(LocalDate checkIn, LocalDate checkOut) throws SQLException {
        List<Integer> list = new ArrayList<>();
        String sql = """
            SELECT DISTINCT room_number FROM bookings
            WHERE status != 'CANCELLED'
              AND check_in_date < ?
              AND check_out_date > ?
        """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, checkOut.toString());
            pstmt.setString(2, checkIn.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getInt(1));
                }
            }
        }
        return list;
    }

    public List<Booking> findByStatus(BookingStatus status) throws SQLException {
        List<Booking> list = new ArrayList<>();
        String sql = """
            SELECT b.*, g.full_name as guest_name, g.phone as guest_phone, r.room_type,
                   COALESCE((SELECT SUM(amount_paise) FROM payments p WHERE p.booking_id = b.id), 0) as paid_paise
            FROM bookings b
            JOIN guests g ON b.guest_id = g.id
            JOIN rooms r ON b.room_number = r.room_number
            WHERE b.status = ?
            ORDER BY b.id DESC
        """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status.name());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    private Booking mapResultSet(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        int guestId = rs.getInt("guest_id");
        int roomNumber = rs.getInt("room_number");
        LocalDate in = LocalDate.parse(rs.getString("check_in_date"));
        LocalDate out = LocalDate.parse(rs.getString("check_out_date"));
        BigDecimal rate = fromPaise(rs.getLong("nightly_rate_paise"));
        int nights = rs.getInt("number_of_nights");
        BigDecimal subtotal = fromPaise(rs.getLong("subtotal_paise"));
        BigDecimal tax = fromPaise(rs.getLong("tax_amount_paise"));
        BigDecimal total = fromPaise(rs.getLong("total_amount_paise"));
        BookingStatus status = BookingStatus.valueOf(rs.getString("status"));
        LocalDateTime created = LocalDateTime.parse(rs.getString("created_at"));

        Booking booking = new Booking(id, guestId, roomNumber, in, out, rate, nights, subtotal, tax, total, status, created);
        booking.setGuestName(rs.getString("guest_name"));
        booking.setGuestPhone(rs.getString("guest_phone"));
        booking.setRoomType(RoomType.valueOf(rs.getString("room_type")));
        booking.setPaidAmount(fromPaise(rs.getLong("paid_paise")));
        return booking;
    }
}
