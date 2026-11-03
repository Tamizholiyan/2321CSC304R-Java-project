package com.campuscommute.campuscommute.rides;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RideRepository extends JpaRepository<Ride, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Ride r WHERE r.rideId = :rideId")
    Optional<Ride> findByIdForUpdate(@Param("rideId") Long rideId);

    @Query("SELECT r FROM Ride r WHERE " +
           "r.rideStatus = com.campuscommute.campuscommute.rides.RideStatus.SCHEDULED AND " +
           "r.availableSeats > 0 AND " +
           "(r.departureTime IS NULL OR r.departureTime > :now) AND " +
           "(:callerId IS NULL OR r.driverId != :callerId) AND " +
           "(:start IS NULL OR :start = '' OR LOWER(r.startLocation) LIKE LOWER(CONCAT('%', :start, '%'))) AND " +
           "(:destination IS NULL OR :destination = '' OR LOWER(r.destination) LIKE LOWER(CONCAT('%', :destination, '%'))) " +
           "ORDER BY r.departureTime ASC, r.rideId DESC")
    List<Ride> searchOpenRides(
            @Param("callerId") Long callerId,
            @Param("now") LocalDateTime now,
            @Param("start") String start,
            @Param("destination") String destination
    );

    @Query("SELECT r FROM Ride r WHERE r.driverId = :driverId AND r.rideStatus IN (com.campuscommute.campuscommute.rides.RideStatus.SCHEDULED, com.campuscommute.campuscommute.rides.RideStatus.ONGOING)")
    List<Ride> findActiveRidesByDriverId(@Param("driverId") Long driverId);

    @Query("SELECT r FROM Ride r WHERE r.driverId = :driverId ORDER BY r.departureTime DESC, r.rideId DESC")
    List<Ride> findByDriverIdOrderByDepartureTimeDesc(@Param("driverId") Long driverId);

    List<Ride> findByDriverId(Long driverId);
}
