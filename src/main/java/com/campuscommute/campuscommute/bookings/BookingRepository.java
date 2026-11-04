package com.campuscommute.campuscommute.bookings;

import com.campuscommute.campuscommute.rides.Ride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    boolean existsByRideIdAndRiderIdAndBookingStatus(Long rideId, Long riderId, BookingStatus bookingStatus);

    boolean existsByRideIdAndBookingStatus(Long rideId, BookingStatus bookingStatus);

    List<Booking> findByRideIdAndBookingStatus(Long rideId, BookingStatus bookingStatus);

    List<Booking> findByRiderId(Long riderId);

    List<Booking> findByRideId(Long rideId);

    @Query("SELECT r FROM Booking b, Ride r WHERE b.rideId = r.rideId " +
           "AND b.riderId = :riderId " +
           "AND b.bookingStatus = com.campuscommute.campuscommute.bookings.BookingStatus.CONFIRMED " +
           "AND r.rideStatus IN (com.campuscommute.campuscommute.rides.RideStatus.SCHEDULED, com.campuscommute.campuscommute.rides.RideStatus.FULL, com.campuscommute.campuscommute.rides.RideStatus.ONGOING)")
    List<Ride> findActiveBookedRidesByRiderId(@Param("riderId") Long riderId);
}
