package com.hotelflow.service;

import com.hotelflow.dao.BookingDao;
import com.hotelflow.dao.PaymentDao;
import com.hotelflow.database.DatabaseManager;
import com.hotelflow.model.Booking;
import com.hotelflow.model.Guest;
import com.hotelflow.model.Payment;
import com.hotelflow.model.PaymentStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class RazorpayPaymentGatewayTest {

    private static final String TEST_KEY_ID = "rzp_test_mock12345678";
    private static final String TEST_KEY_SECRET = "mock_secret_abcdef123456";

    private RazorpayService razorpayService;
    private PaymentDao paymentDao;
    private BookingDao bookingDao;
    private HotelService hotelService;

    @BeforeAll
    static void initDb() {
        new File("target").mkdirs();
        new File("target/test_razorpay.db").delete();
        DatabaseManager.setCustomDbUrl("jdbc:sqlite:target/test_razorpay.db");
        DatabaseManager.initializeDatabase();
    }

    @BeforeEach
    void setUp() {
        RazorpayConfig.setCredentials(TEST_KEY_ID, TEST_KEY_SECRET);
        this.paymentDao = new PaymentDao();
        this.bookingDao = new BookingDao();
        this.razorpayService = new RazorpayService(paymentDao, bookingDao);
        this.hotelService = new HotelService();
    }

    private String calculateExpectedSignature(String orderId, String paymentId, String secret) throws Exception {
        String payload = orderId + "|" + paymentId;
        Mac sha256 = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256.init(keySpec);
        byte[] hash = sha256.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    @Test
    @DisplayName("Verify Razorpay signature verification succeeds for authentic HMAC-SHA256 signature")
    void testValidSignatureVerification() throws Exception {
        String orderId = "order_DBJOWzybf0sJbb";
        String paymentId = "pay_29QQoUBi66xm2f";
        String validSignature = calculateExpectedSignature(orderId, paymentId, TEST_KEY_SECRET);

        boolean isValid = razorpayService.verifySignature(orderId, paymentId, validSignature);
        assertTrue(isValid, "Legitimate HMAC-SHA256 signature from Razorpay must verify successfully");
    }

    @Test
    @DisplayName("Verify Razorpay signature verification fails on forged signature")
    void testInvalidSignatureVerification() {
        String orderId = "order_DBJOWzybf0sJbb";
        String paymentId = "pay_29QQoUBi66xm2f";
        String fakeSignature = "bad_forged_signature_0000000000000000000000000000000000000000";

        boolean isValid = razorpayService.verifySignature(orderId, paymentId, fakeSignature);
        assertFalse(isValid, "Tampered or forged signature must be rejected by backend");
    }

    @Test
    @DisplayName("Verify signature verification fails if order ID or payment ID is tampered with")
    void testTamperedPayloadSignatureVerification() throws Exception {
        String orderId = "order_DBJOWzybf0sJbb";
        String paymentId = "pay_29QQoUBi66xm2f";
        String validSignature = calculateExpectedSignature(orderId, paymentId, TEST_KEY_SECRET);

        // Tamper with order ID
        boolean orderTampered = razorpayService.verifySignature("order_TAMPERED", paymentId, validSignature);
        assertFalse(orderTampered, "Tampered Order ID must fail verification");

        // Tamper with payment ID
        boolean paymentTampered = razorpayService.verifySignature(orderId, "pay_TAMPERED", validSignature);
        assertFalse(paymentTampered, "Tampered Payment ID must fail verification");
    }

    @Test
    @DisplayName("Verify pending payments do NOT reduce booking balance until verified as SUCCESS")
    void testPendingPaymentDoesNotReduceBalance() throws SQLException {
        // Create a guest and booking
        Guest guest = new Guest("Ravi Patel", "9876543299", "ravi@example.com", "Pune");
        int guestId = hotelService.saveGuest(guest);
        int bookingId = hotelService.createBooking(guestId, 102, LocalDate.now().plusDays(30), LocalDate.now().plusDays(32));

        Booking booking = hotelService.getBookingById(bookingId).orElseThrow();
        BigDecimal initialBalance = booking.getBalanceAmount();
        assertEquals(PaymentStatus.UNPAID, booking.getPaymentStatus());

        // Insert a PENDING Razorpay payment record
        Payment pendingPayment = new Payment(
                bookingId,
                new BigDecimal("1500.00"),
                "RAZORPAY",
                "Awaiting user completion",
                "order_pending_123",
                null,
                PaymentStatus.PENDING
        );
        int pId = paymentDao.insert(pendingPayment);
        assertTrue(pId > 0);

        // Verify that booking balance remains unchanged because payment is PENDING
        Booking bookingAfterPending = hotelService.getBookingById(bookingId).orElseThrow();
        assertEquals(initialBalance, bookingAfterPending.getBalanceAmount(),
                "PENDING payment must NOT reduce booking balance");
        assertEquals(PaymentStatus.UNPAID, bookingAfterPending.getPaymentStatus());

        // Update to SUCCESS
        paymentDao.updateStatusByOrderId("order_pending_123", PaymentStatus.SUCCESS, "pay_success_123", "UPI");

        // Verify balance is now updated
        Booking bookingAfterSuccess = hotelService.getBookingById(bookingId).orElseThrow();
        assertEquals(initialBalance.subtract(new BigDecimal("1500.00")), bookingAfterSuccess.getBalanceAmount(),
                "SUCCESS payment MUST reduce booking balance");
        assertNotEquals(PaymentStatus.UNPAID, bookingAfterSuccess.getPaymentStatus());
    }

    @Test
    @DisplayName("Verify duplicate payment attempts are handled safely without double charging")
    void testDuplicatePaymentHandling() throws Exception {
        Guest guest = new Guest("Priya Deshmukh", "9898989898", "priya@example.com", "Pune");
        int guestId = hotelService.saveGuest(guest);
        int bookingId = hotelService.createBooking(guestId, 201, LocalDate.now().plusDays(40), LocalDate.now().plusDays(42));

        String orderId = "order_dup_test_999";
        String paymentId = "pay_dup_test_999";
        BigDecimal amount = new BigDecimal("2000.00");

        // 1. Initial pending payment
        Payment payment = new Payment(bookingId, amount, "RAZORPAY", "Test Dup", orderId, null, PaymentStatus.PENDING);
        paymentDao.insert(payment);

        String validSignature = calculateExpectedSignature(orderId, paymentId, TEST_KEY_SECRET);

        // 2. First completion
        Payment completed1 = razorpayService.verifyAndCompletePayment(orderId, paymentId, validSignature);
        assertEquals(PaymentStatus.SUCCESS, completed1.getStatus());
        assertEquals(paymentId, completed1.getRazorpayPaymentId());

        // 3. Duplicate completion attempt with same order & payment ID
        Payment completed2 = razorpayService.verifyAndCompletePayment(orderId, paymentId, validSignature);
        assertEquals(PaymentStatus.SUCCESS, completed2.getStatus());
        assertEquals(completed1.getId(), completed2.getId(), "Should return existing record instead of creating duplicate");
    }

    @Test
    @DisplayName("Verify failed / cancelled payment is marked FAILED and balance remains due")
    void testCancelledPaymentFlow() throws SQLException {
        Guest guest = new Guest("Vikram Malhotra", "9123456789", "vikram@example.com", "Mumbai");
        int guestId = hotelService.saveGuest(guest);
        int bookingId = hotelService.createBooking(guestId, 203, LocalDate.now().plusDays(50), LocalDate.now().plusDays(52));

        String orderId = "order_cancel_123";
        Payment payment = new Payment(bookingId, new BigDecimal("1000.00"), "RAZORPAY", "Testing cancel", orderId, null, PaymentStatus.PENDING);
        paymentDao.insert(payment);

        // Mark as cancelled/failed
        razorpayService.markPaymentFailed(orderId, "Customer dismissed checkout modal");

        Payment recorded = paymentDao.findByRazorpayOrderId(orderId).orElseThrow();
        assertEquals(PaymentStatus.FAILED, recorded.getStatus());

        Booking booking = hotelService.getBookingById(bookingId).orElseThrow();
        assertEquals(PaymentStatus.UNPAID, booking.getPaymentStatus());
    }

    @Test
    @DisplayName("Verify formal payment receipt generation contains order ID, payment ID, and metadata")
    void testReceiptGeneration() throws SQLException {
        Guest guest = new Guest("Aditi Rao", "9876500000", "aditi@example.com", "Bangalore");
        int guestId = hotelService.saveGuest(guest);
        int bookingId = hotelService.createBooking(guestId, 101, LocalDate.now().plusDays(60), LocalDate.now().plusDays(62));
        Booking booking = hotelService.getBookingById(bookingId).orElseThrow();

        Payment payment = new Payment(
                10,
                bookingId,
                new BigDecimal("3360.00"),
                "UPI",
                "Advance Payment",
                null,
                "order_rcpt_001",
                "pay_rcpt_001",
                PaymentStatus.SUCCESS
        );

        String receipt = razorpayService.generateReceipt(payment, booking);
        assertNotNull(receipt);
        assertTrue(receipt.contains("HOTELFLOW OFFICIAL RECEIPT"));
        assertTrue(receipt.contains("order_rcpt_001"));
        assertTrue(receipt.contains("pay_rcpt_001"));
        assertTrue(receipt.contains("Aditi Rao"));
        assertTrue(receipt.contains("Room #101"));
        assertTrue(receipt.contains("HMAC-SHA256"));
    }
}

