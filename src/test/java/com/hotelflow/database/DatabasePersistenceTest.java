package com.hotelflow.database;

import com.hotelflow.dao.BookingDao;
import com.hotelflow.dao.GuestDao;
import com.hotelflow.dao.PaymentDao;
import com.hotelflow.dao.RoomDao;
import com.hotelflow.model.Booking;
import com.hotelflow.model.Guest;
import com.hotelflow.model.Payment;
import com.hotelflow.model.Room;
import com.hotelflow.service.HotelService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DatabasePersistenceTest {

    @BeforeAll
    static void init() {
        DatabaseManager.initializeDatabase();
    }

    @Test
    @DisplayName("Verify SQLite schema seeds rooms, guests, and allows booking insertion")
    void testDatabaseOperations() throws SQLException {
        HotelService service = new HotelService();

        // 1. Verify rooms are seeded
        List<Room> rooms = service.getAllRooms();
        assertFalse(rooms.isEmpty(), "Rooms should be initialized and seeded");

        // 2. Verify guests are seeded
        List<Guest> guests = service.getAllGuests();
        assertFalse(guests.isEmpty(), "Guests should be initialized and seeded");

        // 3. Create a test guest and booking
        Guest testGuest = new Guest("Test Guest Viva", "9998887776", "test.viva@example.com", "Pune");
        int guestId = service.saveGuest(testGuest);
        assertTrue(guestId > 0, "Guest should be saved and assigned an auto-increment ID");

        // 4. Test booking creation on room 301 for a future date
        LocalDate checkIn = LocalDate.now().plusDays(20);
        LocalDate checkOut = LocalDate.now().plusDays(22);

        int bookingId = service.createBooking(guestId, 301, checkIn, checkOut);
        assertTrue(bookingId > 0, "Booking should be saved with generated ID");

        // 5. Test date conflict prevention on the same room 301
        assertThrows(IllegalStateException.class, () -> {
            service.createBooking(guestId, 301, checkIn.plusDays(1), checkOut.plusDays(1));
        }, "Overlapping dates on room 301 must trigger conflict exception");

        // 6. Test payment recording
        Booking booking = service.getBookingById(bookingId).orElseThrow();
        BigDecimal balance = booking.getBalanceAmount();
        assertTrue(balance.compareTo(BigDecimal.ZERO) > 0, "New booking must have balance due");

        // Partial payment of 1000 INR
        BigDecimal partial = new BigDecimal("1000.00");
        service.recordPayment(bookingId, partial, "UPI", "JUnit Test Payment");

        // Verify updated balance
        Booking updatedBooking = service.getBookingById(bookingId).orElseThrow();
        assertEquals(balance.subtract(partial), updatedBooking.getBalanceAmount(), "Balance must be reduced after payment");
    }
}
