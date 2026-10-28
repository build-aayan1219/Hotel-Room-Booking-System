package com.hotelflow.dao;

import com.hotelflow.database.DatabaseManager;
import com.hotelflow.model.Guest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GuestDao {

    public int insert(Guest guest) throws SQLException {
        String sql = "INSERT INTO guests (full_name, phone, email, address, created_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, guest.getFullName());
            pstmt.setString(2, guest.getPhone());
            pstmt.setString(3, guest.getEmail());
            pstmt.setString(4, guest.getAddress());
            pstmt.setString(5, guest.getCreatedAt() != null ? guest.getCreatedAt().toString() : LocalDateTime.now().toString());
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    guest.setId(id);
                    return id;
                }
            }
        }
        return 0;
    }

    public void update(Guest guest) throws SQLException {
        String sql = "UPDATE guests SET full_name = ?, phone = ?, email = ?, address = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guest.getFullName());
            pstmt.setString(2, guest.getPhone());
            pstmt.setString(3, guest.getEmail());
            pstmt.setString(4, guest.getAddress());
            pstmt.setInt(5, guest.getId());
            pstmt.executeUpdate();
        }
    }

    public void delete(int guestId) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM bookings WHERE guest_id = ?";
        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement chk = conn.prepareStatement(checkSql)) {
                chk.setInt(1, guestId);
                try (ResultSet rs = chk.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        throw new SQLException("Cannot delete Guest #" + guestId + " because they have " + rs.getInt(1) + " booking records on file.");
                    }
                }
            }
            try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM guests WHERE id = ?")) {
                pstmt.setInt(1, guestId);
                pstmt.executeUpdate();
            }
        }
    }

    public Optional<Guest> findById(int id) throws SQLException {
        String sql = "SELECT id, full_name, phone, email, address, created_at FROM guests WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<Guest> findAll() throws SQLException {
        List<Guest> list = new ArrayList<>();
        String sql = "SELECT id, full_name, phone, email, address, created_at FROM guests ORDER BY full_name ASC";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    public List<Guest> search(String query) throws SQLException {
        List<Guest> list = new ArrayList<>();
        String sql = "SELECT id, full_name, phone, email, address, created_at FROM guests WHERE full_name LIKE ? OR phone LIKE ? OR email LIKE ? ORDER BY full_name ASC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            String pattern = "%" + query.trim() + "%";
            pstmt.setString(1, pattern);
            pstmt.setString(2, pattern);
            pstmt.setString(3, pattern);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    private Guest mapResultSet(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String name = rs.getString("full_name");
        String phone = rs.getString("phone");
        String email = rs.getString("email");
        String address = rs.getString("address");
        String createdStr = rs.getString("created_at");
        LocalDateTime created = LocalDateTime.parse(createdStr);
        return new Guest(id, name, phone, email, address, created);
    }
}
