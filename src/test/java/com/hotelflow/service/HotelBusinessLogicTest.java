package com.hotelflow.service;

import com.hotelflow.model.Booking;
import com.hotelflow.model.BookingStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class HotelBusinessLogicTest {

    @Test
    @DisplayName("Verify date range validation rejects invalid dates")
    void testDateRangeValidation() {
        LocalDate today = LocalDate.now();

        // Check-out same day as check-in should fail
        assertThrows(IllegalArgumentException.class, () -> {
            HotelService.validateDateRange(today, today);
        });

        // Check-out before check-in should fail
        assertThrows(IllegalArgumentException.class, () -> {
            HotelService.validateDateRange(today, today.minusDays(1));
        });

        // Valid range should pass without exception
        assertDoesNotThrow(() -> {
            HotelService.validateDateRange(today, today.plusDays(2));
        });
    }

    @Test
    @DisplayName("Verify billing calculation matches exact mathematical requirements")
    void testBillingCalculations() {
        Booking booking = new Booking();
        booking.setCheckInDate(LocalDate.of(2026, 11, 1));
        booking.setCheckOutDate(LocalDate.of(2026, 11, 4)); // 3 nights
        booking.setNightlyRate(new BigDecimal("2500.00"));

        BigDecimal taxRate = new BigDecimal("0.12"); // 12% GST
        booking.calculateBilling(taxRate);

        assertEquals(3, booking.getNumberOfNights(), "Nights should be exactly 3");
        assertEquals(new BigDecimal("7500.00"), booking.getSubtotal(), "Subtotal: 3 * 2500 = 7500.00");
        assertEquals(new BigDecimal("900.00"), booking.getTaxAmount(), "Tax: 7500 * 0.12 = 900.00");
        assertEquals(new BigDecimal("8400.00"), booking.getTotalAmount(), "Total: 7500 + 900 = 8400.00");

        // Test payment balance calculation
        booking.setPaidAmount(new BigDecimal("3000.00"));
        assertEquals(new BigDecimal("5400.00"), booking.getBalanceAmount(), "Balance should be 8400 - 3000 = 5400.00");
    }

    @Test
    @DisplayName("Verify overlap condition rule: existingCheckIn < newCheckOut && existingCheckOut > newCheckIn")
    void testDateOverlapCondition() {
        // Existing booking: Nov 10 to Nov 15
        LocalDate existingIn = LocalDate.of(2026, 11, 10);
        LocalDate existingOut = LocalDate.of(2026, 11, 15);

        // Case 1: Same day turnaround (departs Nov 10, guest arrives Nov 10) -> Should NOT conflict
        LocalDate newIn1 = LocalDate.of(2026, 11, 5);
        LocalDate newOut1 = LocalDate.of(2026, 11, 10);
        boolean conflict1 = existingIn.isBefore(newOut1) && existingOut.isAfter(newIn1);
        assertFalse(conflict1, "Checking out on Nov 10 must allow check-in on Nov 10");

        // Case 2: Guest checking in on Nov 15 when previous departs Nov 15 -> Should NOT conflict
        LocalDate newIn2 = LocalDate.of(2026, 11, 15);
        LocalDate newOut2 = LocalDate.of(2026, 11, 18);
        boolean conflict2 = existingIn.isBefore(newOut2) && existingOut.isAfter(newIn2);
        assertFalse(conflict2, "Checking in on Nov 15 must be allowed when prior guest checks out Nov 15");

        // Case 3: Partial overlap (Nov 12 to Nov 17) -> MUST conflict
        LocalDate newIn3 = LocalDate.of(2026, 11, 12);
        LocalDate newOut3 = LocalDate.of(2026, 11, 17);
        boolean conflict3 = existingIn.isBefore(newOut3) && existingOut.isAfter(newIn3);
        assertTrue(conflict3, "Overlapping range (Nov 12 to 17) must trigger a conflict");

        // Case 4: Enclosing overlap (Nov 8 to Nov 20) -> MUST conflict
        LocalDate newIn4 = LocalDate.of(2026, 11, 8);
        LocalDate newOut4 = LocalDate.of(2026, 11, 20);
        boolean conflict4 = existingIn.isBefore(newOut4) && existingOut.isAfter(newIn4);
        assertTrue(conflict4, "Enclosing range must trigger a conflict");
    }
}
