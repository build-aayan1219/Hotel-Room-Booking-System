package com.hotelflow.model;

import java.math.BigDecimal;

public enum RoomType {
    SINGLE("Single", new BigDecimal("1.00"), "Cozy single bed for solo travelers"),
    DOUBLE("Double", new BigDecimal("1.50"), "Comfortable queen bed for couples"),
    DELUXE("Deluxe", new BigDecimal("2.20"), "Spacious room with king bed & city view"),
    SUITE("Suite", new BigDecimal("3.00"), "Luxury multi-room suite with premium amenities");

    private final String displayName;
    private final BigDecimal multiplier;
    private final String description;

    RoomType(String displayName, BigDecimal multiplier, String description) {
        this.displayName = displayName;
        this.multiplier = multiplier;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public BigDecimal getMultiplier() {
        return multiplier;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
