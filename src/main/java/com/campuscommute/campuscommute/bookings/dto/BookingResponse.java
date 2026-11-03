package com.campuscommute.campuscommute.bookings.dto;

import com.campuscommute.campuscommute.bookings.Booking;
import com.campuscommute.campuscommute.bookings.BookingStatus;
import com.campuscommute.campuscommute.rides.Ride;
import com.campuscommute.campuscommute.rides.RideStatus;

import java.time.LocalDateTime;

public record BookingResponse(
        Long bookingId,
        Long rideId,
        Long riderId,
        LocalDateTime bookingTime,
        BookingStatus bookingStatus,
        // Ride summary (for rider view)
        String startLocation,
        String destination,
        String vehicleModel,
        String vehiclePlate,
        LocalDateTime departureTime,
        RideStatus rideStatus,
        String driverName,
        String driverPhone,
        // Rider details (for driver view)
        String riderName,
        String riderDepartment,
        String riderPhone
) {
    public static BookingResponse of(
            Booking booking,
            Ride ride,
            String driverName,
            String driverPhone,
            String riderName,
            String riderDepartment,
            String riderPhone
    ) {
        return new BookingResponse(
                booking.getBookingId(),
                booking.getRideId(),
                booking.getRiderId(),
                booking.getBookingTime(),
                booking.getBookingStatus(),
                ride != null ? ride.getStartLocation() : null,
                ride != null ? ride.getDestination() : null,
                ride != null ? ride.getVehicleModel() : null,
                ride != null ? ride.getVehiclePlate() : null,
                ride != null ? ride.getDepartureTime() : null,
                ride != null ? ride.getRideStatus() : null,
                driverName,
                (booking.getBookingStatus() == BookingStatus.CONFIRMED) ? driverPhone : null,
                riderName,
                riderDepartment,
                riderPhone
        );
    }

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getBookingId(),
                booking.getRideId(),
                booking.getRiderId(),
                booking.getBookingTime(),
                booking.getBookingStatus(),
                null, null, null, null, null, null, null, null, null, null, null
        );
    }
}
