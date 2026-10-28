package com.hotelflow.model;

public enum BookingStatus {
    CONFIRMED("Confirmed"),
    CHECKED_IN("Checked In"),
    CHECKED_OUT("Checked Out"),
    CANCELLED("Cancelled");

    private final String displayName;

    BookingStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Checks if this status is considered active for room date conflicts.
     * Cancelled bookings do not block dates.
     */
    public boolean blocksRoom() {
        return this != CANCELLED;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
