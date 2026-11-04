package com.hotelflow.service;

import com.hotelflow.database.DatabaseManager;
import com.hotelflow.model.User;
import com.hotelflow.model.UserRole;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AuthServiceTest {

    private static AuthService authService;

    @BeforeAll
    static void init() {
        new File("target").mkdirs();
        new File("target/test_auth_hotelflow.db").delete();
        DatabaseManager.setCustomDbUrl("jdbc:sqlite:target/test_auth_hotelflow.db");
        DatabaseManager.initializeDatabase();
        authService = new AuthService();
    }

    @Test
    @DisplayName("Verify default administrator can log in successfully")
    void testAdminLoginSuccess() throws Exception {
        User user = authService.login("admin", "admin123");
        assertNotNull(user);
        assertEquals("admin", user.getUsername());
        assertEquals(UserRole.ADMIN, user.getRole());
        assertTrue(authService.isLoggedIn());
        assertTrue(authService.isAdmin());
    }

    @Test
    @DisplayName("Verify default staff member can log in successfully")
    void testStaffLoginSuccess() throws Exception {
        User user = authService.login("staff", "staff123");
        assertNotNull(user);
        assertEquals("staff", user.getUsername());
        assertEquals(UserRole.RECEPTIONIST, user.getRole());
        assertFalse(user.isAdmin());
    }

    @Test
    @DisplayName("Verify login rejection on invalid password")
    void testInvalidPasswordFails() {
        assertThrows(AuthService.AuthenticationException.class, () -> {
            authService.login("admin", "wrong_password");
        });
    }

    @Test
    @DisplayName("Verify login rejection on nonexistent username")
    void testNonexistentUserFails() {
        assertThrows(AuthService.AuthenticationException.class, () -> {
            authService.login("ghost_user", "password");
        });
    }

    @Test
    @DisplayName("Verify registering a new user and authenticating with new credentials")
    void testRegisterAndAuthenticateNewUser() throws Exception {
        User newUser = authService.registerUser("reception_clerk", "clerkPass123", "Pooja Patel", UserRole.RECEPTIONIST);
        assertNotNull(newUser);
        assertTrue(newUser.getId() > 0);

        // Verify login works with new user
        User loggedIn = authService.login("reception_clerk", "clerkPass123");
        assertEquals("reception_clerk", loggedIn.getUsername());
        assertEquals("Pooja Patel", loggedIn.getFullName());

        // Test logout
        authService.logout();
        assertNull(authService.getCurrentUser());
        assertFalse(authService.isLoggedIn());
    }
}
