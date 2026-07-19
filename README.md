# Hotel Booking Management System

A Java 17+ console application for managing hotel room bookings in memory.

## Features

- Lists 10 pre-seeded hotel rooms
- Supports Single, Double, and Suite room types
- Searches available rooms by type and stay dates
- Creates, views, and cancels bookings
- Checks guests in and out
- Prevents overlapping bookings for the same room
- Generates bills with nights, rate, tax, and total
- Uses `LocalDate` for dates and `BigDecimal` for accurate money calculations

## Room Rates

Room rates are calculated from a base nightly rate of `$100.00`:

| Room Type | Multiplier | Nightly Rate |
|---|---:|---:|
| Single | 1.0 | $100.00 |
| Double | 1.2 | $120.00 |
| Suite | 1.6 | $160.00 |

## Requirements

- Java Development Kit (JDK) 17 or newer

## Run the Project

```bash
javac HotelApp.java
java HotelApp
```

## Booking Rule

The application uses date-range overlap logic to ensure that two active bookings cannot reserve the same room for overlapping dates.

## Technologies Used

- Java 17+
- Object-Oriented Programming
- `LocalDate`
- `BigDecimal`
- `Scanner`
- In-memory data storage
