package com.campuscommute.campuscommute.bookings.dto;

import com.campuscommute.campuscommute.bookings.Booking;
import com.campuscommute.campuscommute.bookings.BookingStatus;

import java.time.LocalDateTime;

public record BookingResponse(
        Long bookingId,
        Long rideId,
        Long riderId,
        LocalDateTime bookingTime,
        BookingStatus bookingStatus
) {
    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getBookingId(),
                booking.getRideId(),
                booking.getRiderId(),
                booking.getBookingTime(),
                booking.getBookingStatus()
        );
    }
}

