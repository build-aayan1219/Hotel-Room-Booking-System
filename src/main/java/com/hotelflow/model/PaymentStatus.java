package com.hotelflow.model;

/**
 * Status enumeration for payment transactions and booking-level payment tracking.
 */
public enum PaymentStatus {
    // Transaction-level statuses (Razorpay Gateway & Payment records)
    PENDING("Pending"),
    SUCCESS("Success"),
    FAILED("Failed"),
    REFUNDED("Refunded"),

    // Booking-level balance aggregation statuses
    UNPAID("Unpaid"),
    PARTIALLY_PAID("Partially Paid"),
    PAID("Paid");

    private final String label;

    PaymentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }

    public static PaymentStatus fromString(String text) {
        if (text == null || text.isBlank()) {
            return SUCCESS;
        }
        for (PaymentStatus status : values()) {
            if (status.name().equalsIgnoreCase(text.trim()) || status.label.equalsIgnoreCase(text.trim())) {
                return status;
            }
        }
        return SUCCESS;
    }
}
