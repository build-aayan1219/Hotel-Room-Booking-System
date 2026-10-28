package com.hotelflow.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a registered guest in the hotel.
 */
public class Guest {
    private int id;
    private String fullName;
    private String phone;
    private String email;
    private String address;
    private LocalDateTime createdAt;

    public Guest() {
        this.createdAt = LocalDateTime.now();
    }

    public Guest(int id, String fullName, String phone, String email, String address, LocalDateTime createdAt) {
        this.id = id;
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.address = address != null ? address : "";
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public Guest(String fullName, String phone, String email, String address) {
        this(0, fullName, phone, email, address, LocalDateTime.now());
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Guest guest)) return false;
        return id == guest.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return fullName + (phone != null && !phone.isBlank() ? " (" + phone + ")" : "");
    }
}
