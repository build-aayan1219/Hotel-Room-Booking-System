package com.hotelflow.model;

/**
 * Roles for system users:
 * - ADMIN: Full access (User management, Room CRUD, financial reports)
 * - RECEPTIONIST: Front-desk operational access (Bookings, Check-In/Out, Guests, Billing)
 */
public enum UserRole {
    ADMIN("Administrator"),
    RECEPTIONIST("Front Desk Staff");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
