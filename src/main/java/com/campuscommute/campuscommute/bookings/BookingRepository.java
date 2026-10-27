package com.campuscommute.campuscommute.bookings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    boolean existsByRideIdAndRiderIdAndBookingStatus(Long rideId, Long riderId, BookingStatus bookingStatus);

    List<Booking> findByRideIdAndBookingStatus(Long rideId, BookingStatus bookingStatus);

    List<Booking> findByRiderId(Long riderId);

    List<Booking> findByRideId(Long rideId);
}

