package com.hotelflow.dao;

import com.hotelflow.database.DatabaseManager;
import com.hotelflow.model.Room;
import com.hotelflow.model.RoomStatus;
import com.hotelflow.model.RoomType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RoomDao {

    public static long toPaise(BigDecimal amount) {
        if (amount == null) return 0L;
        return amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static BigDecimal fromPaise(long paise) {
        return BigDecimal.valueOf(paise).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public void insert(Room room) throws SQLException {
        String sql = "INSERT INTO rooms (room_number, room_type, nightly_rate_paise, floor, description, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, room.getRoomNumber());
            pstmt.setString(2, room.getType().name());
            pstmt.setLong(3, toPaise(room.getNightlyRate()));
            pstmt.setInt(4, room.getFloor());
            pstmt.setString(5, room.getDescription());
            pstmt.setString(6, room.getStatus().name());
            pstmt.executeUpdate();
        }
    }

    public void update(Room room) throws SQLException {
        String sql = "UPDATE rooms SET room_type = ?, nightly_rate_paise = ?, floor = ?, description = ?, status = ? WHERE room_number = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, room.getType().name());
            pstmt.setLong(2, toPaise(room.getNightlyRate()));
            pstmt.setInt(3, room.getFloor());
            pstmt.setString(4, room.getDescription());
            pstmt.setString(5, room.getStatus().name());
            pstmt.setInt(6, room.getRoomNumber());
            pstmt.executeUpdate();
        }
    }

    public void delete(int roomNumber) throws SQLException {
        // Enforce safe deletion: check if referenced in bookings first
        String checkSql = "SELECT COUNT(*) FROM bookings WHERE room_number = ?";
        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement chk = conn.prepareStatement(checkSql)) {
                chk.setInt(1, roomNumber);
                try (ResultSet rs = chk.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        throw new SQLException("Cannot delete Room #" + roomNumber + " because it has " + rs.getInt(1) + " associated booking records. Consider setting status to MAINTENANCE instead.");
                    }
                }
            }
            try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM rooms WHERE room_number = ?")) {
                pstmt.setInt(1, roomNumber);
                pstmt.executeUpdate();
            }
        }
    }

    public Optional<Room> findByNumber(int roomNumber) throws SQLException {
        String sql = "SELECT room_number, room_type, nightly_rate_paise, floor, description, status FROM rooms WHERE room_number = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, roomNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<Room> findAll() throws SQLException {
        List<Room> list = new ArrayList<>();
        String sql = "SELECT room_number, room_type, nightly_rate_paise, floor, description, status FROM rooms ORDER BY room_number ASC";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    private Room mapResultSet(ResultSet rs) throws SQLException {
        int roomNumber = rs.getInt("room_number");
        RoomType type = RoomType.valueOf(rs.getString("room_type"));
        BigDecimal rate = fromPaise(rs.getLong("nightly_rate_paise"));
        int floor = rs.getInt("floor");
        String desc = rs.getString("description");
        RoomStatus status = RoomStatus.valueOf(rs.getString("status"));
        return new Room(roomNumber, type, rate, floor, desc, status);
    }
}
