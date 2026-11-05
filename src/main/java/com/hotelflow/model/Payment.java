package com.hotelflow.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Represents a payment transaction linked to a room booking.
 * Supports Razorpay payment gateway integration (Order ID, Payment ID, Status).
 */
public class Payment {
    private int id;
    private int bookingId;
    private BigDecimal amount;
    private String paymentMethod; // e.g. "UPI", "CREDIT_CARD", "DEBIT_CARD", "NET_BANKING", "CASH", "RAZORPAY"
    private String referenceNote;
    private LocalDateTime paymentDate;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private PaymentStatus status;

    public Payment() {
        this.paymentDate = LocalDateTime.now();
        this.amount = BigDecimal.ZERO;
        this.paymentMethod = "UPI";
        this.status = PaymentStatus.SUCCESS;
    }

    public Payment(int id, int bookingId, BigDecimal amount, String paymentMethod, String referenceNote,
                   LocalDateTime paymentDate, String razorpayOrderId, String razorpayPaymentId, PaymentStatus status) {
        this.id = id;
        this.bookingId = bookingId;
        this.amount = amount != null ? amount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        this.paymentMethod = paymentMethod != null ? paymentMethod : "UPI";
        this.referenceNote = referenceNote != null ? referenceNote : "";
        this.paymentDate = paymentDate != null ? paymentDate : LocalDateTime.now();
        this.razorpayOrderId = razorpayOrderId;
        this.razorpayPaymentId = razorpayPaymentId;
        this.status = status != null ? status : PaymentStatus.SUCCESS;
    }

    public Payment(int id, int bookingId, BigDecimal amount, String paymentMethod, String referenceNote, LocalDateTime paymentDate) {
        this(id, bookingId, amount, paymentMethod, referenceNote, paymentDate, null, null, PaymentStatus.SUCCESS);
    }

    public Payment(int bookingId, BigDecimal amount, String paymentMethod, String referenceNote) {
        this(0, bookingId, amount, paymentMethod, referenceNote, LocalDateTime.now(), null, null, PaymentStatus.SUCCESS);
    }

    public Payment(int bookingId, BigDecimal amount, String paymentMethod, String referenceNote,
                   String razorpayOrderId, String razorpayPaymentId, PaymentStatus status) {
        this(0, bookingId, amount, paymentMethod, referenceNote, LocalDateTime.now(), razorpayOrderId, razorpayPaymentId, status);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getBookingId() {
        return bookingId;
    }

    public void setBookingId(int bookingId) {
        this.bookingId = bookingId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount != null ? amount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getReferenceNote() {
        return referenceNote;
    }

    public void setReferenceNote(String referenceNote) {
        this.referenceNote = referenceNote;
    }

    public LocalDateTime getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDateTime paymentDate) {
        this.paymentDate = paymentDate;
    }

    public String getRazorpayOrderId() {
        return razorpayOrderId;
    }

    public void setRazorpayOrderId(String razorpayOrderId) {
        this.razorpayOrderId = razorpayOrderId;
    }

    public String getRazorpayPaymentId() {
        return razorpayPaymentId;
    }

    public void setRazorpayPaymentId(String razorpayPaymentId) {
        this.razorpayPaymentId = razorpayPaymentId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status != null ? status : PaymentStatus.SUCCESS;
    }

    @Override
    public String toString() {
        return "Payment #" + id + " - ₹" + amount + " (" + status + ") via " + paymentMethod;
    }
}
