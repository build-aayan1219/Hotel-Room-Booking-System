package com.hotelflow.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Represents a room booking transaction.
 */
public class Booking {
    private int id;
    private int guestId;
    private int roomNumber;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private BigDecimal nightlyRate;
    private int numberOfNights;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BookingStatus status;
    private LocalDateTime createdAt;

    // Transient hydrated fields for convenient display in tables & views
    private String guestName;
    private String guestPhone;
    private RoomType roomType;
    private BigDecimal paidAmount = BigDecimal.ZERO;

    public Booking() {
        this.status = BookingStatus.CONFIRMED;
        this.createdAt = LocalDateTime.now();
        this.nightlyRate = BigDecimal.ZERO;
        this.subtotal = BigDecimal.ZERO;
        this.taxAmount = BigDecimal.ZERO;
        this.totalAmount = BigDecimal.ZERO;
    }

    public Booking(int id, int guestId, int roomNumber, LocalDate checkInDate, LocalDate checkOutDate,
                   BigDecimal nightlyRate, int numberOfNights, BigDecimal subtotal, BigDecimal taxAmount,
                   BigDecimal totalAmount, BookingStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.guestId = guestId;
        this.roomNumber = roomNumber;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.nightlyRate = nightlyRate != null ? nightlyRate.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        this.numberOfNights = numberOfNights;
        this.subtotal = subtotal != null ? subtotal.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        this.taxAmount = taxAmount != null ? taxAmount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        this.totalAmount = totalAmount != null ? totalAmount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        this.status = status != null ? status : BookingStatus.CONFIRMED;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    /**
     * Calculates nights and amounts based on dates, nightly rate, and tax rate.
     */
    public void calculateBilling(BigDecimal taxRate) {
        if (checkInDate != null && checkOutDate != null && nightlyRate != null) {
            long nights = ChronoUnit.DAYS.between(checkInDate, checkOutDate);
            this.numberOfNights = (int) Math.max(1, nights);
            this.subtotal = nightlyRate.multiply(BigDecimal.valueOf(this.numberOfNights)).setScale(2, RoundingMode.HALF_UP);
            this.taxAmount = subtotal.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
            this.totalAmount = subtotal.add(taxAmount).setScale(2, RoundingMode.HALF_UP);
        }
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getGuestId() {
        return guestId;
    }

    public void setGuestId(int guestId) {
        this.guestId = guestId;
    }

    public int getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(int roomNumber) {
        this.roomNumber = roomNumber;
    }

    public LocalDate getCheckInDate() {
        return checkInDate;
    }

    public void setCheckInDate(LocalDate checkInDate) {
        this.checkInDate = checkInDate;
    }

    public LocalDate getCheckOutDate() {
        return checkOutDate;
    }

    public void setCheckOutDate(LocalDate checkOutDate) {
        this.checkOutDate = checkOutDate;
    }

    public BigDecimal getNightlyRate() {
        return nightlyRate;
    }

    public void setNightlyRate(BigDecimal nightlyRate) {
        this.nightlyRate = nightlyRate != null ? nightlyRate.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    public int getNumberOfNights() {
        return numberOfNights;
    }

    public void setNumberOfNights(int numberOfNights) {
        this.numberOfNights = numberOfNights;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getGuestName() {
        return guestName != null ? guestName : "Guest #" + guestId;
    }

    public void setGuestName(String guestName) {
        this.guestName = guestName;
    }

    public String getGuestPhone() {
        return guestPhone;
    }

    public void setGuestPhone(String guestPhone) {
        this.guestPhone = guestPhone;
    }

    public RoomType getRoomType() {
        return roomType;
    }

    public void setRoomType(RoomType roomType) {
        this.roomType = roomType;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount != null ? paidAmount : BigDecimal.ZERO;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount != null ? paidAmount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    public BigDecimal getBalanceAmount() {
        BigDecimal total = totalAmount != null ? totalAmount : BigDecimal.ZERO;
        BigDecimal paid = getPaidAmount();
        BigDecimal balance = total.subtract(paid);
        return balance.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : balance.setScale(2, RoundingMode.HALF_UP);
    }

    public PaymentStatus getPaymentStatus() {
        BigDecimal paid = getPaidAmount();
        BigDecimal total = totalAmount != null ? totalAmount : BigDecimal.ZERO;
        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            return PaymentStatus.UNPAID;
        } else if (paid.compareTo(total) >= 0) {
            return PaymentStatus.PAID;
        } else {
            return PaymentStatus.PARTIALLY_PAID;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Booking booking)) return false;
        return id == booking.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Booking #" + id + " - Room " + roomNumber + " (" + status + ")";
    }
}
