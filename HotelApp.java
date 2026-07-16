import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/** A small in-memory hotel booking console application. */
public class HotelApp {
    private static final BigDecimal BASE_RATE = new BigDecimal("100.00");
    private static final BigDecimal TAX_RATE = new BigDecimal("0.12");

    private final Scanner scanner = new Scanner(System.in);
    private final List<Room> rooms = new ArrayList<>();
    private final List<Booking> bookings = new ArrayList<>();
    private int nextBookingId = 1001;

    public static void main(String[] args) {
        new HotelApp().run();
    }

    private void run() {
        seedRooms();
        System.out.println("Welcome to Hotel Booking Manager");
        boolean running = true;
        while (running) {
            printMenu();
            switch (readInt("Choose an option: ")) {
                case 1 -> listRooms();
                case 2 -> searchAvailableRooms();
                case 3 -> createBooking();
                case 4 -> viewBooking();
                case 5 -> cancelBooking();
                case 6 -> checkIn();
                case 7 -> checkOut();
                case 8 -> printBill();
                case 0 -> running = false;
                default -> System.out.println("Please choose a valid menu option.");
            }
        }
        System.out.println("Thank you for using Hotel Booking Manager.");
    }

    private void printMenu() {
        System.out.println("\n--- MENU ---");
        System.out.println("1. List rooms");
        System.out.println("2. Search available rooms");
        System.out.println("3. Create booking");
        System.out.println("4. View booking by ID");
        System.out.println("5. Cancel booking");
        System.out.println("6. Check in");
        System.out.println("7. Check out");
        System.out.println("8. Print bill");
        System.out.println("0. Exit");
    }

    private void seedRooms() {
        addRoom(101, RoomType.SINGLE); addRoom(102, RoomType.SINGLE); addRoom(103, RoomType.SINGLE); addRoom(104, RoomType.SINGLE);
        addRoom(201, RoomType.DOUBLE); addRoom(202, RoomType.DOUBLE); addRoom(203, RoomType.DOUBLE);
        addRoom(301, RoomType.SUITE); addRoom(302, RoomType.SUITE); addRoom(303, RoomType.SUITE);
    }

    private void addRoom(int number, RoomType type) {
        rooms.add(new Room(number, type, rateFor(type)));
    }

    private BigDecimal rateFor(RoomType type) {
        return BASE_RATE.multiply(type.multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private void listRooms() {
        System.out.printf("%-8s %-10s %12s%n", "Room", "Type", "Nightly Rate");
        rooms.stream().sorted(Comparator.comparingInt(r -> r.number)).forEach(System.out::println);
    }

    private void searchAvailableRooms() {
        RoomType type = readRoomType();
        LocalDate checkIn = readDate("Check-in date (YYYY-MM-DD): ");
        LocalDate checkOut = readDate("Check-out date (YYYY-MM-DD): ");
        if (!validDateRange(checkIn, checkOut)) return;

        List<Room> available = availableRooms(type, checkIn, checkOut);
        if (available.isEmpty()) {
            System.out.println("No " + type + " rooms are available for those dates.");
            return;
        }
        System.out.println("Available rooms:");
        available.forEach(System.out::println);
    }

    private void createBooking() {
        String guest = readNonBlank("Guest name: ");
        RoomType type = readRoomType();
        LocalDate checkIn = readDate("Check-in date (YYYY-MM-DD): ");
        LocalDate checkOut = readDate("Check-out date (YYYY-MM-DD): ");
        if (!validDateRange(checkIn, checkOut)) return;

        List<Room> available = availableRooms(type, checkIn, checkOut);
        if (available.isEmpty()) {
            System.out.println("No matching rooms are available.");
            return;
        }
        System.out.println("Available room numbers: " + available.stream().map(r -> String.valueOf(r.number)).toList());
        int roomNumber = readInt("Choose a room number: ");
        Room room = available.stream().filter(r -> r.number == roomNumber).findFirst().orElse(null);
        if (room == null) {
            System.out.println("That room is not available for the requested dates.");
            return;
        }
        Booking booking = new Booking(nextBookingId++, guest, room, checkIn, checkOut);
        bookings.add(booking);
        System.out.println("Booking created successfully. Booking ID: " + booking.id);
    }

    private List<Room> availableRooms(RoomType type, LocalDate checkIn, LocalDate checkOut) {
        return rooms.stream().filter(r -> r.type == type)
                .filter(r -> bookings.stream().noneMatch(b -> b.room.number == r.number
                        && b.status != BookingStatus.CANCELLED && b.overlaps(checkIn, checkOut)))
                .sorted(Comparator.comparingInt(r -> r.number)).toList();
    }

    private void viewBooking() {
        Booking booking = findBooking();
        if (booking != null) System.out.println(booking.details());
    }

    private void cancelBooking() {
        Booking booking = findBooking();
        if (booking == null) return;
        if (booking.status == BookingStatus.CANCELLED || booking.status == BookingStatus.CHECKED_OUT) {
            System.out.println("This booking cannot be cancelled.");
            return;
        }
        booking.status = BookingStatus.CANCELLED;
        System.out.println("Booking " + booking.id + " cancelled.");
    }

    private void checkIn() {
        Booking booking = findBooking();
        if (booking == null) return;
        if (booking.status != BookingStatus.CONFIRMED) {
            System.out.println("Only confirmed bookings can be checked in.");
            return;
        }
        booking.status = BookingStatus.CHECKED_IN;
        System.out.println("Guest checked in successfully.");
    }

    private void checkOut() {
        Booking booking = findBooking();
        if (booking == null) return;
        if (booking.status != BookingStatus.CHECKED_IN) {
            System.out.println("Only checked-in guests can be checked out.");
            return;
        }
        booking.status = BookingStatus.CHECKED_OUT;
        System.out.println("Guest checked out successfully.");
    }

    private void printBill() {
        Booking booking = findBooking();
        if (booking == null) return;
        long nights = booking.nights();
        BigDecimal roomCharge = booking.room.nightlyRate.multiply(BigDecimal.valueOf(nights));
        BigDecimal tax = roomCharge.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = roomCharge.add(tax).setScale(2, RoundingMode.HALF_UP);
        System.out.println("\n--- BILL ---");
        System.out.println("Booking ID: " + booking.id + " | Guest: " + booking.guestName);
        System.out.println("Room: " + booking.room.number + " (" + booking.room.type + ")");
        System.out.println("Nights: " + nights);
        System.out.println("Rate per night: " + money(booking.room.nightlyRate));
        System.out.println("Room charge: " + money(roomCharge));
        System.out.println("Tax (12%): " + money(tax));
        System.out.println("Total: " + money(total));
    }

    private Booking findBooking() {
        int id = readInt("Booking ID: ");
        Booking booking = bookings.stream().filter(b -> b.id == id).findFirst().orElse(null);
        if (booking == null) System.out.println("No booking found with ID " + id + ".");
        return booking;
    }

    private RoomType readRoomType() {
        while (true) {
            System.out.print("Room type (Single, Double, Suite): ");
            try { return RoomType.valueOf(scanner.nextLine().trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { System.out.println("Enter Single, Double, or Suite."); }
        }
    }

    private LocalDate readDate(String prompt) {
        while (true) {
            System.out.print(prompt);
            try { return LocalDate.parse(scanner.nextLine().trim()); }
            catch (DateTimeParseException e) { System.out.println("Use YYYY-MM-DD, for example 2026-08-15."); }
        }
    }

    private int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            try { return Integer.parseInt(scanner.nextLine().trim()); }
            catch (NumberFormatException e) { System.out.println("Enter a whole number."); }
        }
    }

    private String readNonBlank(String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = scanner.nextLine().trim();
            if (!value.isEmpty()) return value;
            System.out.println("This value cannot be blank.");
        }
    }

    private boolean validDateRange(LocalDate checkIn, LocalDate checkOut) {
        if (!checkOut.isAfter(checkIn)) {
            System.out.println("Check-out must be after check-in.");
            return false;
        }
        return true;
    }

    private static String money(BigDecimal amount) { return "$" + amount.setScale(2, RoundingMode.HALF_UP); }

    enum RoomType {
        SINGLE(new BigDecimal("1.0")), DOUBLE(new BigDecimal("1.2")), SUITE(new BigDecimal("1.6"));
        final BigDecimal multiplier;
        RoomType(BigDecimal multiplier) { this.multiplier = multiplier; }
    }

    enum BookingStatus { CONFIRMED, CHECKED_IN, CHECKED_OUT, CANCELLED }

    static class Room {
        final int number;
        final RoomType type;
        final BigDecimal nightlyRate;
        Room(int number, RoomType type, BigDecimal nightlyRate) { this.number = number; this.type = type; this.nightlyRate = nightlyRate; }
        public String toString() { return String.format("%-8d %-10s %11s", number, type, money(nightlyRate)); }
    }

    static class Booking {
        final int id;
        final String guestName;
        final Room room;
        final LocalDate checkIn;
        final LocalDate checkOut;
        BookingStatus status = BookingStatus.CONFIRMED;
        Booking(int id, String guestName, Room room, LocalDate checkIn, LocalDate checkOut) {
            this.id = id; this.guestName = guestName; this.room = room; this.checkIn = checkIn; this.checkOut = checkOut;
        }
        long nights() { return ChronoUnit.DAYS.between(checkIn, checkOut); }
        // [start, end) intervals overlap when each begins before the other ends.
        boolean overlaps(LocalDate start, LocalDate end) { return checkIn.isBefore(end) && start.isBefore(checkOut); }
        String details() {
            return "Booking ID: " + id + "\nGuest: " + guestName + "\nRoom: " + room.number + " (" + room.type + ")"
                    + "\nStay: " + checkIn + " to " + checkOut + " (" + nights() + " nights)\nStatus: " + status;
        }
    }
}
