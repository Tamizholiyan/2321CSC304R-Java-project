package com.campuscommute.campuscommute.bookings;

public class BookingNotFoundException extends RuntimeException {
    public BookingNotFoundException(String message) {
        super(message);
    }

    public BookingNotFoundException(Long bookingId) {
        super("Booking not found with id: " + bookingId);
    }
}

