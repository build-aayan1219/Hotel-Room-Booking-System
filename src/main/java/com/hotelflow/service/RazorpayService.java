package com.hotelflow.service;

import com.hotelflow.dao.BookingDao;
import com.hotelflow.dao.PaymentDao;
import com.hotelflow.model.Booking;
import com.hotelflow.model.Payment;
import com.hotelflow.model.PaymentStatus;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.security.SignatureException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Enterprise-grade Razorpay Payment Gateway Service.
 * Handles server-side Order creation, HMAC-SHA256 signature verification,
 * duplicate transaction prevention, failure recovery, and receipt generation.
 */
public class RazorpayService {

    private final PaymentDao paymentDao;
    private final BookingDao bookingDao;

    public RazorpayService() {
        this.paymentDao = new PaymentDao();
        this.bookingDao = new BookingDao();
    }

    public RazorpayService(PaymentDao paymentDao, BookingDao bookingDao) {
        this.paymentDao = paymentDao;
        this.bookingDao = bookingDao;
    }

    /**
     * Data Transfer Object carrying Razorpay Order information to the frontend checkout view.
     */
    public record RazorpayOrderDto(
            String orderId,
            long amountPaise,
            BigDecimal amountRupees,
            String currency,
            String keyId,
            int bookingId,
            int roomNumber,
            String guestName,
            String guestEmail,
            String guestPhone,
            int paymentRecordId,
            String receiptNumber
    ) {}

    /**
     * Initializes an official RazorpayClient instance using configured credentials.
     */
    private RazorpayClient getClient() throws RazorpayException {
        if (!RazorpayConfig.isConfigured()) {
            throw new IllegalStateException("Razorpay API credentials are not configured. "
                    + "Please set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET in your environment or config.properties.");
        }
        return new RazorpayClient(RazorpayConfig.getKeyId(), RazorpayConfig.getKeySecret());
    }

    /**
     * Creates a new server-side Razorpay Order and initiates a PENDING Payment record.
     */
    public RazorpayOrderDto createOrder(Booking booking, BigDecimal amountToPay) throws RazorpayException, SQLException {
        if (booking == null) {
            throw new IllegalArgumentException("Booking cannot be null.");
        }
        if (amountToPay == null || amountToPay.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero.");
        }

        BigDecimal balance = booking.getBalanceAmount();
        if (amountToPay.compareTo(balance) > 0) {
            throw new IllegalArgumentException("Payment amount (₹" + amountToPay + ") exceeds outstanding balance (₹" + balance + ").");
        }

        long amountPaise = PaymentDao.toPaise(amountToPay);
        String receiptId = "rcpt_b" + booking.getId() + "_" + System.currentTimeMillis();

        // 1. Create order on Razorpay servers via official Java SDK
        RazorpayClient client = getClient();
        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amountPaise);
        orderRequest.put("currency", RazorpayConfig.CURRENCY_INR);
        orderRequest.put("receipt", receiptId);

        JSONObject notes = new JSONObject();
        notes.put("booking_id", String.valueOf(booking.getId()));
        notes.put("room_number", String.valueOf(booking.getRoomNumber()));
        notes.put("guest_name", booking.getGuestName());
        notes.put("guest_phone", booking.getGuestPhone() != null ? booking.getGuestPhone() : "");
        orderRequest.put("notes", notes);

        Order order = client.orders.create(orderRequest);
        String orderId = order.get("id");

        // 2. Persist initial payment record as PENDING in local database
        Payment payment = new Payment(
                booking.getId(),
                amountToPay,
                "RAZORPAY",
                "Razorpay Order: " + orderId,
                orderId,
                null,
                PaymentStatus.PENDING
        );
        int paymentId = paymentDao.insert(payment);

        return new RazorpayOrderDto(
                orderId,
                amountPaise,
                amountToPay,
                RazorpayConfig.CURRENCY_INR,
                RazorpayConfig.getKeyId(),
                booking.getId(),
                booking.getRoomNumber(),
                booking.getGuestName(),
                booking.getGuestPhone() != null ? booking.getGuestPhone() : "",
                booking.getGuestPhone() != null ? booking.getGuestPhone() : "",
                paymentId,
                receiptId
        );
    }

    /**
     * Verifies the cryptographic HMAC-SHA256 signature returned by Razorpay Checkout.
     * Uses Razorpay's official verification algorithm.
     */
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        if (orderId == null || paymentId == null || signature == null) {
            return false;
        }
        try {
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", orderId);
            attributes.put("razorpay_payment_id", paymentId);
            attributes.put("razorpay_signature", signature);

            return Utils.verifyPaymentSignature(attributes, RazorpayConfig.getKeySecret());
        } catch (Exception e) {
            System.err.println("[RazorpayService] Signature verification exception: " + e.getMessage());
            return false;
        }
    }

    /**
     * Server-side payment completion handler:
     * 1. Validates signature cryptographically
     * 2. Guards against duplicate processing
     * 3. Fetches payment method (UPI, Card, NetBanking) from Razorpay
     * 4. Updates payment status to SUCCESS in database
     * 5. Hydrates and returns updated Payment
     */
    public Payment verifyAndCompletePayment(String orderId, String paymentId, String signature)
            throws SQLException, SecurityException, RazorpayException {

        if (orderId == null || orderId.isBlank() || paymentId == null || paymentId.isBlank()) {
            throw new IllegalArgumentException("Razorpay Order ID and Payment ID are mandatory.");
        }

        // 1. Check existing payment record
        Optional<Payment> optPayment = paymentDao.findByRazorpayOrderId(orderId);
        if (optPayment.isEmpty()) {
            // Also check by payment ID
            optPayment = paymentDao.findByRazorpayPaymentId(paymentId);
        }

        if (optPayment.isPresent() && optPayment.get().getStatus() == PaymentStatus.SUCCESS) {
            System.out.println("[RazorpayService] Payment already verified & marked SUCCESS. Preventing duplicate charge.");
            return optPayment.get();
        }

        // 2. Server-side signature verification
        boolean validSignature = verifySignature(orderId, paymentId, signature);
        if (!validSignature) {
            // Mark payment as FAILED
            if (optPayment.isPresent()) {
                paymentDao.updateStatusByOrderId(orderId, PaymentStatus.FAILED, paymentId, "FAILED");
            }
            throw new SecurityException("Razorpay signature verification failed! The payment response was tampered with or is invalid.");
        }

        // 3. Query Razorpay API to inspect actual payment method & status
        String detectedMethod = "RAZORPAY";
        try {
            RazorpayClient client = getClient();
            com.razorpay.Payment rzpPayment = client.payments.fetch(paymentId);
            if (rzpPayment.has("method") && rzpPayment.get("method") != null) {
                detectedMethod = rzpPayment.get("method").toString().toUpperCase();
            }
        } catch (Exception e) {
            System.err.println("[RazorpayService] Note: Could not fetch payment method from API (" + e.getMessage() + "), using fallback.");
        }

        // 4. Update database record to SUCCESS
        paymentDao.updateStatusByOrderId(orderId, PaymentStatus.SUCCESS, paymentId, detectedMethod);

        // 5. Reload payment
        Payment completedPayment = paymentDao.findByRazorpayOrderId(orderId)
                .orElseGet(() -> paymentDao.findByRazorpayPaymentId(paymentId).orElse(null));

        if (completedPayment == null) {
            throw new IllegalStateException("Payment record not found for Order ID: " + orderId);
        }

        System.out.println("[RazorpayService] Payment SUCCESS verified for Order: " + orderId + ", Payment ID: " + paymentId);
        return completedPayment;
    }

    /**
     * Marks a pending payment as FAILED (e.g. user dismissed checkout, card declined, or network error).
     */
    public void markPaymentFailed(String orderId, String reason) {
        if (orderId == null || orderId.isBlank()) return;
        try {
            paymentDao.updateStatusByOrderId(orderId, PaymentStatus.FAILED, null, "CANCELLED");
            System.out.println("[RazorpayService] Order " + orderId + " marked as FAILED/CANCELLED: " + reason);
        } catch (SQLException e) {
            System.err.println("[RazorpayService] Error updating failed payment: " + e.getMessage());
        }
    }

    /**
     * Generates a formal, formatted receipt slip for a successful Razorpay transaction.
     */
    public String generateReceipt(Payment payment, Booking booking) {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");
        String txnDate = payment.getPaymentDate() != null ? payment.getPaymentDate().format(dtf) : LocalDateTime.now().format(dtf);

        sb.append("================================================================\n");
        sb.append("                  HOTELFLOW OFFICIAL RECEIPT                    \n");
        sb.append("              RAZORPAY PAYMENT GATEWAY VERIFIED                 \n");
        sb.append("================================================================\n");
        sb.append(String.format("Receipt Number    : RCPT-%d-%d%n", booking.getId(), payment.getId()));
        sb.append(String.format("Transaction Date  : %s%n", txnDate));
        sb.append(String.format("Payment Status    : %s%n", payment.getStatus().name()));
        sb.append("----------------------------------------------------------------\n");
        sb.append("BOOKING & GUEST DETAILS:\n");
        sb.append(String.format("  Booking ID      : #%d%n", booking.getId()));
        sb.append(String.format("  Guest Name      : %s%n", booking.getGuestName()));
        sb.append(String.format("  Contact Phone   : %s%n", booking.getGuestPhone() != null ? booking.getGuestPhone() : "N/A"));
        sb.append(String.format("  Room Reserved   : Room #%d (%s)%n", booking.getRoomNumber(), booking.getRoomType()));
        sb.append(String.format("  Stay Duration   : %s to %s (%d Night(s))%n",
                booking.getCheckInDate(), booking.getCheckOutDate(), booking.getNumberOfNights()));
        sb.append("----------------------------------------------------------------\n");
        sb.append("GATEWAY TRANSACTION METADATA:\n");
        sb.append(String.format("  Razorpay Order  : %s%n", payment.getRazorpayOrderId() != null ? payment.getRazorpayOrderId() : "N/A"));
        sb.append(String.format("  Razorpay Payment: %s%n", payment.getRazorpayPaymentId() != null ? payment.getRazorpayPaymentId() : "N/A"));
        sb.append(String.format("  Payment Channel : %s%n", payment.getPaymentMethod()));
        sb.append(String.format("  Security Hash   : HMAC-SHA256 (Server Verified)%n"));
        sb.append("----------------------------------------------------------------\n");
        sb.append(String.format("Total Booking Due : INR %s%n", payment.getAmount().setScale(2)));
        sb.append(String.format("Amount Received   : INR %s%n", payment.getAmount().setScale(2)));
        sb.append(String.format("Remaining Balance : INR %s%n", booking.getBalanceAmount().setScale(2)));
        sb.append("================================================================\n");
        sb.append("        Thank you for your business at HotelFlow!               \n");
        sb.append("   This document is a computer-generated official receipt.      \n");
        sb.append("================================================================\n");

        return sb.toString();
    }
}
