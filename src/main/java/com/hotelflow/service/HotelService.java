package com.hotelflow.service;

import com.hotelflow.dao.BookingDao;
import com.hotelflow.dao.GuestDao;
import com.hotelflow.dao.PaymentDao;
import com.hotelflow.dao.RoomDao;
import com.hotelflow.model.Booking;
import com.hotelflow.model.BookingStatus;
import com.hotelflow.model.Guest;
import com.hotelflow.model.Payment;
import com.hotelflow.model.Room;
import com.hotelflow.model.RoomStatus;
import com.hotelflow.model.RoomType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Core business service implementing validation, availability, booking workflows, check-in/out, and billing.
 */
public class HotelService {
    private final RoomDao roomDao;
    private final GuestDao guestDao;
    private final BookingDao bookingDao;
    private final PaymentDao paymentDao;

    public HotelService() {
        this.roomDao = new RoomDao();
        this.guestDao = new GuestDao();
        this.bookingDao = new BookingDao();
        this.paymentDao = new PaymentDao();
    }

    public HotelService(RoomDao roomDao, GuestDao guestDao, BookingDao bookingDao, PaymentDao paymentDao) {
        this.roomDao = roomDao;
        this.guestDao = guestDao;
        this.bookingDao = bookingDao;
        this.paymentDao = paymentDao;
    }

    // ==================== ROOM MANAGEMENT ====================

    public List<Room> getAllRooms() throws SQLException {
        return roomDao.findAll();
    }

    public void addRoom(Room room) throws SQLException {
        if (room.getRoomNumber() <= 0) {
            throw new IllegalArgumentException("Room number must be a positive integer.");
        }
        if (room.getNightlyRate() == null || room.getNightlyRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Nightly rate must be greater than zero.");
        }
        if (roomDao.findByNumber(room.getRoomNumber()).isPresent()) {
            throw new IllegalArgumentException("Room #" + room.getRoomNumber() + " already exists.");
        }
        roomDao.insert(room);
    }

    public void updateRoom(Room room) throws SQLException {
        if (room.getNightlyRate() == null || room.getNightlyRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Nightly rate must be greater than zero.");
        }
        roomDao.update(room);
    }

    public void deleteRoom(int roomNumber) throws SQLException {
        roomDao.delete(roomNumber);
    }

    // ==================== GUEST MANAGEMENT ====================

    public List<Guest> getAllGuests() throws SQLException {
        return guestDao.findAll();
    }

    public List<Guest> searchGuests(String query) throws SQLException {
        if (query == null || query.isBlank()) {
            return guestDao.findAll();
        }
        return guestDao.search(query);
    }

    public int saveGuest(Guest guest) throws SQLException {
        if (guest.getFullName() == null || guest.getFullName().trim().isBlank()) {
            throw new IllegalArgumentException("Guest full name is required.");
        }
        if (guest.getId() > 0) {
            guestDao.update(guest);
            return guest.getId();
        } else {
            return guestDao.insert(guest);
        }
    }

    public void deleteGuest(int guestId) throws SQLException {
        guestDao.delete(guestId);
    }

    // ==================== AVAILABILITY & BOOKINGS ====================

    /**
     * Finds active rooms of specified type (or all if type is null) that do not have overlapping active bookings.
     */
    public List<Room> getAvailableRooms(RoomType type, LocalDate checkIn, LocalDate checkOut) throws SQLException {
        validateDateRange(checkIn, checkOut);
        List<Room> allRooms = roomDao.findAll();
        List<Integer> conflictingRoomNumbers = bookingDao.getConflictingRoomNumbers(checkIn, checkOut);

        return allRooms.stream()
                .filter(r -> r.getStatus() == RoomStatus.ACTIVE)
                .filter(r -> type == null || r.getType() == type)
                .filter(r -> !conflictingRoomNumbers.contains(r.getRoomNumber()))
                .collect(Collectors.toList());
    }

    public static void validateDateRange(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null) {
            throw new IllegalArgumentException("Check-in and check-out dates are required.");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("Check-out date must be strictly after check-in date.");
        }
    }

    public int createBooking(int guestId, int roomNumber, LocalDate checkIn, LocalDate checkOut) throws SQLException {
        validateDateRange(checkIn, checkOut);

        // Check if room exists and is active
        Room room = roomDao.findByNumber(roomNumber)
                .orElseThrow(() -> new IllegalArgumentException("Room #" + roomNumber + " not found."));
        if (room.getStatus() != RoomStatus.ACTIVE) {
            throw new IllegalStateException("Room #" + roomNumber + " is currently under maintenance.");
        }

        // Check conflict using: existingCheckIn < newCheckOut && existingCheckOut > newCheckIn
        if (bookingDao.hasConflict(roomNumber, checkIn, checkOut, 0)) {
            throw new IllegalStateException("Room #" + roomNumber + " is not available between " + checkIn + " and " + checkOut + ".");
        }

        // Calculate billing
        Booking booking = new Booking();
        booking.setGuestId(guestId);
        booking.setRoomNumber(roomNumber);
        booking.setCheckInDate(checkIn);
        booking.setCheckOutDate(checkOut);
        booking.setNightlyRate(room.getNightlyRate());
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.calculateBilling(HotelConfig.getTaxRate());

        return bookingDao.insert(booking);
    }

    public List<Booking> getAllBookings() throws SQLException {
        return bookingDao.findAll();
    }

    public Optional<Booking> getBookingById(int id) throws SQLException {
        return bookingDao.findById(id);
    }

    public void cancelBooking(int bookingId) throws SQLException {
        Booking booking = bookingDao.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking #" + bookingId + " not found."));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Booking #" + bookingId + " is already cancelled.");
        }
        if (booking.getStatus() == BookingStatus.CHECKED_OUT) {
            throw new IllegalStateException("Cannot cancel a booking that has already checked out.");
        }

        bookingDao.updateStatus(bookingId, BookingStatus.CANCELLED);
    }

    // ==================== CHECK-IN & CHECK-OUT ====================

    public void checkIn(int bookingId) throws SQLException {
        Booking booking = bookingDao.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking #" + bookingId + " not found."));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only CONFIRMED bookings can be checked in. Current status: " + booking.getStatus());
        }

        bookingDao.updateStatus(bookingId, BookingStatus.CHECKED_IN);
    }

    public void checkOut(int bookingId) throws SQLException {
        Booking booking = bookingDao.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking #" + bookingId + " not found."));

        if (booking.getStatus() != BookingStatus.CHECKED_IN) {
            throw new IllegalStateException("Only CHECKED_IN guests can be checked out. Current status: " + booking.getStatus());
        }

        // Check outstanding balance
        BigDecimal balance = booking.getBalanceAmount();
        if (balance.compareTo(BigDecimal.ZERO) > 0) {
            // Note: The system can warn or require payment. We will still allow check-out if approved,
            // but provide validation indicator in the UI.
        }

        bookingDao.updateStatus(bookingId, BookingStatus.CHECKED_OUT);
    }

    // ==================== BILLING & PAYMENTS ====================

    public void recordPayment(int bookingId, BigDecimal amount, String method, String note) throws SQLException {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero.");
        }

        Booking booking = bookingDao.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking #" + bookingId + " not found."));

        BigDecimal balance = booking.getBalanceAmount();
        if (amount.compareTo(balance) > 0) {
            throw new IllegalArgumentException("Payment amount (₹" + amount + ") exceeds outstanding balance (₹" + balance + ").");
        }

        Payment payment = new Payment(bookingId, amount, method, note);
        paymentDao.insert(payment);
    }

    public List<Payment> getPaymentsForBooking(int bookingId) throws SQLException {
        return paymentDao.findByBookingId(bookingId);
    }

    public List<Payment> getAllPayments() throws SQLException {
        return paymentDao.findAll();
    }

    public BigDecimal getTodayRevenue() throws SQLException {
        return paymentDao.getRevenueForDate(LocalDate.now());
    }

    public BigDecimal getTotalRevenue() throws SQLException {
        return paymentDao.getTotalRevenue();
    }

    // ==================== DASHBOARD METRICS ====================

    public DashboardMetrics getDashboardMetrics() throws SQLException {
        List<Room> rooms = roomDao.findAll();
        List<Booking> bookings = bookingDao.findAll();
        LocalDate today = LocalDate.now();

        int totalRooms = rooms.size();
        int maintenanceRooms = (int) rooms.stream().filter(r -> r.getStatus() == RoomStatus.MAINTENANCE).count();
        int operationalRooms = totalRooms - maintenanceRooms;

        // Currently occupied rooms are those with status CHECKED_IN
        int occupiedRooms = (int) bookings.stream().filter(b -> b.getStatus() == BookingStatus.CHECKED_IN).count();
        int availableRooms = Math.max(0, operationalRooms - occupiedRooms);

        double occupancyRate = operationalRooms > 0 ? ((double) occupiedRooms / operationalRooms) * 100.0 : 0.0;

        // Today's scheduled check-ins and check-outs
        int todayCheckIns = (int) bookings.stream()
                .filter(b -> b.getCheckInDate().equals(today) && (b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.CHECKED_IN))
                .count();

        int todayCheckOuts = (int) bookings.stream()
                .filter(b -> b.getCheckOutDate().equals(today) && (b.getStatus() == BookingStatus.CHECKED_IN || b.getStatus() == BookingStatus.CHECKED_OUT))
                .count();

        BigDecimal todayRevenue = paymentDao.getRevenueForDate(today);
        BigDecimal totalRevenue = paymentDao.getTotalRevenue();

        return new DashboardMetrics(
                totalRooms,
                operationalRooms,
                occupiedRooms,
                maintenanceRooms,
                availableRooms,
                todayCheckIns,
                todayCheckOuts,
                todayRevenue,
                totalRevenue,
                occupancyRate
        );
    }

    public record DashboardMetrics(
            int totalRooms,
            int operationalRooms,
            int occupiedRooms,
            int maintenanceRooms,
            int availableRooms,
            int todayCheckIns,
            int todayCheckOuts,
            BigDecimal todayRevenue,
            BigDecimal totalRevenue,
            double occupancyRate
    ) {}
}
