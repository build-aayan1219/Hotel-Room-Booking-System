package com.hotelflow.model;

public enum RoomStatus {
    ACTIVE("Active"),
    MAINTENANCE("Under Maintenance");

    private final String label;

    RoomStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
