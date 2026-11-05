package com.hotelflow.dao;

import com.hotelflow.database.DatabaseManager;
import com.hotelflow.model.Payment;
import com.hotelflow.model.PaymentStatus;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PaymentDao {

    public static long toPaise(BigDecimal amount) {
        if (amount == null) return 0L;
        return amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static BigDecimal fromPaise(long paise) {
        return BigDecimal.valueOf(paise).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public int insert(Payment payment) throws SQLException {
        String sql = """
            INSERT INTO payments (booking_id, amount_paise, payment_method, reference_note, payment_date,
                                  razorpay_order_id, razorpay_payment_id, payment_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, payment.getBookingId());
            pstmt.setLong(2, toPaise(payment.getAmount()));
            pstmt.setString(3, payment.getPaymentMethod());
            pstmt.setString(4, payment.getReferenceNote());
            pstmt.setString(5, payment.getPaymentDate() != null ? payment.getPaymentDate().toString() : LocalDateTime.now().toString());
            pstmt.setString(6, payment.getRazorpayOrderId());
            pstmt.setString(7, payment.getRazorpayPaymentId());
            pstmt.setString(8, payment.getStatus() != null ? payment.getStatus().name() : PaymentStatus.SUCCESS.name());
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    payment.setId(id);
                    return id;
                }
            }
        }
        return 0;
    }

    public void updateStatusAndPaymentId(int paymentId, PaymentStatus status, String razorpayPaymentId, String paymentMethod) throws SQLException {
        String sql = "UPDATE payments SET payment_status = ?, razorpay_payment_id = ?, payment_method = COALESCE(?, payment_method) WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status.name());
            pstmt.setString(2, razorpayPaymentId);
            pstmt.setString(3, paymentMethod);
            pstmt.setInt(4, paymentId);
            pstmt.executeUpdate();
        }
    }

    public void updateStatusByOrderId(String orderId, PaymentStatus status, String razorpayPaymentId, String paymentMethod) throws SQLException {
        String sql = "UPDATE payments SET payment_status = ?, razorpay_payment_id = ?, payment_method = COALESCE(?, payment_method) WHERE razorpay_order_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status.name());
            pstmt.setString(2, razorpayPaymentId);
            pstmt.setString(3, paymentMethod);
            pstmt.setString(4, orderId);
            pstmt.executeUpdate();
        }
    }

    public Optional<Payment> findById(int id) throws SQLException {
        String sql = """
            SELECT id, booking_id, amount_paise, payment_method, reference_note, payment_date,
                   razorpay_order_id, razorpay_payment_id, payment_status
            FROM payments WHERE id = ?
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

    public Optional<Payment> findByRazorpayOrderId(String orderId) throws SQLException {
        if (orderId == null || orderId.isBlank()) return Optional.empty();
        String sql = """
            SELECT id, booking_id, amount_paise, payment_method, reference_note, payment_date,
                   razorpay_order_id, razorpay_payment_id, payment_status
            FROM payments WHERE razorpay_order_id = ?
        """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, orderId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Payment> findByRazorpayPaymentId(String paymentId) throws SQLException {
        if (paymentId == null || paymentId.isBlank()) return Optional.empty();
        String sql = """
            SELECT id, booking_id, amount_paise, payment_method, reference_note, payment_date,
                   razorpay_order_id, razorpay_payment_id, payment_status
            FROM payments WHERE razorpay_payment_id = ?
        """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, paymentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<Payment> findByBookingId(int bookingId) throws SQLException {
        List<Payment> list = new ArrayList<>();
        String sql = """
            SELECT id, booking_id, amount_paise, payment_method, reference_note, payment_date,
                   razorpay_order_id, razorpay_payment_id, payment_status
            FROM payments WHERE booking_id = ? ORDER BY id ASC
        """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, bookingId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    public List<Payment> findAll() throws SQLException {
        List<Payment> list = new ArrayList<>();
        String sql = """
            SELECT id, booking_id, amount_paise, payment_method, reference_note, payment_date,
                   razorpay_order_id, razorpay_payment_id, payment_status
            FROM payments ORDER BY id DESC
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

    public BigDecimal getTotalRevenue() throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount_paise), 0) FROM payments WHERE (payment_status = 'SUCCESS' OR payment_status IS NULL)";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return fromPaise(rs.getLong(1));
            }
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal getRevenueForDate(LocalDate date) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount_paise), 0) FROM payments WHERE payment_date LIKE ? AND (payment_status = 'SUCCESS' OR payment_status IS NULL)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, date.toString() + "%");
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return fromPaise(rs.getLong(1));
                }
            }
        }
        return BigDecimal.ZERO;
    }

    public Map<String, BigDecimal> getDailyRevenueLastDays(int days) throws SQLException {
        Map<String, BigDecimal> result = new HashMap<>();
        String sql = """
            SELECT substr(payment_date, 1, 10) as pdate, SUM(amount_paise) as total_paise
            FROM payments
            WHERE (payment_status = 'SUCCESS' OR payment_status IS NULL)
            GROUP BY substr(payment_date, 1, 10)
            ORDER BY pdate DESC
            LIMIT ?
        """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, days);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("pdate"), fromPaise(rs.getLong("total_paise")));
                }
            }
        }
        return result;
    }

    private Payment mapResultSet(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        int bookingId = rs.getInt("booking_id");
        BigDecimal amount = fromPaise(rs.getLong("amount_paise"));
        String method = rs.getString("payment_method");
        String note = rs.getString("reference_note");
        LocalDateTime date;
        try {
            date = LocalDateTime.parse(rs.getString("payment_date"));
        } catch (Exception e) {
            date = LocalDateTime.now();
        }
        String orderId = rs.getString("razorpay_order_id");
        String paymentId = rs.getString("razorpay_payment_id");
        String statusStr = rs.getString("payment_status");
        PaymentStatus status = PaymentStatus.fromString(statusStr);

        return new Payment(id, bookingId, amount, method, note, date, orderId, paymentId, status);
    }
}
