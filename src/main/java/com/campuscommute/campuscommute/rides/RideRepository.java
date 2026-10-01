package com.campuscommute.campuscommute.rides;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RideRepository extends JpaRepository<Ride, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Ride r WHERE r.rideId = :rideId")
    Optional<Ride> findByIdForUpdate(@Param("rideId") Long rideId);

    @Query("SELECT r FROM Ride r WHERE " +
           "(:start IS NULL OR :start = '' OR LOWER(r.startLocation) LIKE LOWER(CONCAT('%', :start, '%'))) AND " +
           "(:destination IS NULL OR :destination = '' OR LOWER(r.destination) LIKE LOWER(CONCAT('%', :destination, '%')))")
    List<Ride> searchRides(@Param("start") String start, @Param("destination") String destination);

    List<Ride> findByDriverId(Long driverId);
}
