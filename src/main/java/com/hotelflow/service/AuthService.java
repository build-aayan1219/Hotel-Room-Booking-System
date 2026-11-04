package com.hotelflow.service;

import com.hotelflow.database.DatabaseManager;
import com.hotelflow.model.User;
import com.hotelflow.model.UserRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Authentication and User Session Service.
 * Manages user logins, password verification, active sessions, and user management.
 * Shared by both the Eclipse Terminal interface and the JavaFX GUI.
 */
public class AuthService {

    private User currentUser;

    public AuthService() {
        this.currentUser = null;
    }

    /**
     * Authenticates a user with username and plain-text password.
     * Hashes the password using SHA-256 before comparing with the database.
     *
     * @param username user login handle
     * @param password raw plain-text password
     * @return the authenticated User
     * @throws AuthenticationException if username/password is invalid
     * @throws SQLException if a database query error occurs
     */
    public User login(String username, String password) throws AuthenticationException, SQLException {
        if (username == null || username.trim().isEmpty()) {
            throw new AuthenticationException("Username cannot be empty.");
        }
        if (password == null || password.isEmpty()) {
            throw new AuthenticationException("Password cannot be empty.");
        }

        String inputHash = DatabaseManager.hashPassword(password);
        String sql = "SELECT id, username, password_hash, full_name, role, created_at FROM users WHERE username = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username.trim());

            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    throw new AuthenticationException("Invalid credentials: user '" + username.trim() + "' does not exist.");
                }

                String storedHash = rs.getString("password_hash");
                if (!storedHash.equals(inputHash)) {
                    throw new AuthenticationException("Invalid credentials: incorrect password entered.");
                }

                int id = rs.getInt("id");
                String uname = rs.getString("username");
                String fullName = rs.getString("full_name");
                UserRole role = UserRole.valueOf(rs.getString("role"));
                LocalDateTime created = LocalDateTime.parse(rs.getString("created_at"));

                User authenticatedUser = new User(id, uname, storedHash, fullName, role, created);
                this.currentUser = authenticatedUser;
                return authenticatedUser;
            }
        }
    }

    /**
     * Logs out the current user and clears the active session.
     */
    public void logout() {
        this.currentUser = null;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public boolean isAdmin() {
        return currentUser != null && currentUser.isAdmin();
    }

    /**
     * Retrieves all registered system users (for Admin review).
     */
    public List<User> getAllUsers() throws SQLException {
        List<User> users = new ArrayList<>();
        String sql = "SELECT id, username, password_hash, full_name, role, created_at FROM users ORDER BY id ASC";

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int id = rs.getInt("id");
                String uname = rs.getString("username");
                String hash = rs.getString("password_hash");
                String fullName = rs.getString("full_name");
                UserRole role = UserRole.valueOf(rs.getString("role"));
                LocalDateTime created = LocalDateTime.parse(rs.getString("created_at"));
                users.add(new User(id, uname, hash, fullName, role, created));
            }
        }
        return users;
    }

    /**
     * Registers a new user account (Admin permission required).
     */
    public User registerUser(String username, String rawPassword, String fullName, UserRole role)
            throws AuthenticationException, SQLException {
        if (username == null || username.trim().isEmpty()) {
            throw new AuthenticationException("Username is required.");
        }
        if (rawPassword == null || rawPassword.length() < 4) {
            throw new AuthenticationException("Password must be at least 4 characters long.");
        }
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new AuthenticationException("Full name is required.");
        }

        // Check for duplicate username
        String checkSql = "SELECT COUNT(*) FROM users WHERE username = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement chk = conn.prepareStatement(checkSql)) {
            chk.setString(1, username.trim().toLowerCase());
            try (ResultSet rs = chk.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    throw new AuthenticationException("Username '" + username.trim() + "' is already taken.");
                }
            }
        }

        String hash = DatabaseManager.hashPassword(rawPassword);
        String insertSql = "INSERT INTO users (username, password_hash, full_name, role, created_at) VALUES (?, ?, ?, ?, ?)";
        LocalDateTime now = LocalDateTime.now();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, username.trim().toLowerCase());
            pstmt.setString(2, hash);
            pstmt.setString(3, fullName.trim());
            pstmt.setString(4, role != null ? role.name() : UserRole.RECEPTIONIST.name());
            pstmt.setString(5, now.toString());
            pstmt.executeUpdate();

            int newId = 0;
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) newId = rs.getInt(1);
            }
            return new User(newId, username.trim().toLowerCase(), hash, fullName.trim(), role, now);
        }
    }

    /**
     * Deletes a user by ID.
     */
    public void deleteUser(int userId) throws AuthenticationException, SQLException {
        if (currentUser != null && currentUser.getId() == userId) {
            throw new AuthenticationException("You cannot delete your own logged-in account!");
        }

        // Prevent deleting the last administrator
        String countAdminSql = "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(countAdminSql)) {
            if (rs.next() && rs.getInt(1) <= 1) {
                // Check if target is admin
                String checkRoleSql = "SELECT role FROM users WHERE id = ?";
                try (PreparedStatement chk = conn.prepareStatement(checkRoleSql)) {
                    chk.setInt(1, userId);
                    try (ResultSet rRole = chk.executeQuery()) {
                        if (rRole.next() && "ADMIN".equals(rRole.getString(1))) {
                            throw new AuthenticationException("Cannot delete the last remaining Administrator account.");
                        }
                    }
                }
            }
        }

        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.executeUpdate();
        }
    }

    /**
     * Custom exception for authentication and credential validation errors.
     */
    public static class AuthenticationException extends Exception {
        public AuthenticationException(String message) {
            super(message);
        }
    }
}
