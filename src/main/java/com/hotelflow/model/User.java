package com.hotelflow.model;

import java.time.LocalDateTime;

/**
 * Represents an authenticated user of the HotelFlow system.
 */
public class User {
    private int id;
    private String username;
    private String passwordHash;
    private String fullName;
    private UserRole role;
    private LocalDateTime createdAt;

    public User() {
        this.role = UserRole.RECEPTIONIST;
        this.createdAt = LocalDateTime.now();
    }

    public User(int id, String username, String passwordHash, String fullName, UserRole role, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role != null ? role : UserRole.RECEPTIONIST;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isAdmin() {
        return this.role == UserRole.ADMIN;
    }

    @Override
    public String toString() {
        return fullName + " (" + username + " - " + role.getDisplayName() + ")";
    }
}
