package com.hotelflow.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Represents a hotel room.
 * Demonstrates encapsulation with private fields, getters, setters, and business methods.
 */
public class Room {
    private int roomNumber;
    private RoomType type;
    private BigDecimal nightlyRate; // in INR
    private int floor;
    private String description;
    private RoomStatus status;

    public Room() {
        this.status = RoomStatus.ACTIVE;
        this.nightlyRate = BigDecimal.ZERO;
    }

    public Room(int roomNumber, RoomType type, BigDecimal nightlyRate, int floor, String description, RoomStatus status) {
        this.roomNumber = roomNumber;
        this.type = type;
        this.nightlyRate = nightlyRate != null ? nightlyRate.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        this.floor = floor;
        this.description = description != null ? description : "";
        this.status = status != null ? status : RoomStatus.ACTIVE;
    }

    public int getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(int roomNumber) {
        this.roomNumber = roomNumber;
    }

    public RoomType getType() {
        return type;
    }

    public void setType(RoomType type) {
        this.type = type;
    }

    public BigDecimal getNightlyRate() {
        return nightlyRate;
    }

    public void setNightlyRate(BigDecimal nightlyRate) {
        this.nightlyRate = nightlyRate != null ? nightlyRate.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    public int getFloor() {
        return floor;
    }

    public void setFloor(int floor) {
        this.floor = floor;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
    }

    public boolean isOperational() {
        return this.status == RoomStatus.ACTIVE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Room room)) return false;
        return roomNumber == room.roomNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(roomNumber);
    }

    @Override
    public String toString() {
        return "Room #" + roomNumber + " (" + (type != null ? type.getDisplayName() : "Unknown") + " - ₹" + nightlyRate + ")";
    }
}
