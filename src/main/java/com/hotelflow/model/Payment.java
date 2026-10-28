package com.hotelflow.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

public class Payment {
    private int id;
    private int bookingId;
    private BigDecimal amount;
    private String paymentMethod; // e.g. "CASH", "UPI", "CREDIT_CARD", "DEBIT_CARD"
    private String referenceNote;
    private LocalDateTime paymentDate;

    public Payment() {
        this.paymentDate = LocalDateTime.now();
        this.amount = BigDecimal.ZERO;
        this.paymentMethod = "UPI";
    }

    public Payment(int id, int bookingId, BigDecimal amount, String paymentMethod, String referenceNote, LocalDateTime paymentDate) {
        this.id = id;
        this.bookingId = bookingId;
        this.amount = amount != null ? amount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        this.paymentMethod = paymentMethod != null ? paymentMethod : "UPI";
        this.referenceNote = referenceNote != null ? referenceNote : "";
        this.paymentDate = paymentDate != null ? paymentDate : LocalDateTime.now();
    }

    public Payment(int bookingId, BigDecimal amount, String paymentMethod, String referenceNote) {
        this(0, bookingId, amount, paymentMethod, referenceNote, LocalDateTime.now());
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
}
