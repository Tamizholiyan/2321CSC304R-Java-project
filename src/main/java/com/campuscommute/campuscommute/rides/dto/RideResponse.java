package com.campuscommute.campuscommute.rides.dto;

import com.campuscommute.campuscommute.rides.Ride;
import com.campuscommute.campuscommute.rides.RideStatus;
import com.campuscommute.campuscommute.rides.VehicleType;

import java.math.BigDecimal;

public record RideResponse(
        Long rideId,
        Long driverId,
        String startLocation,
        String destination,
        VehicleType vehicleType,
        BigDecimal mileageKmpl,
        BigDecimal totalDistanceKm,
        Integer availableSeats,
        RideStatus rideStatus
) {
    public static RideResponse from(Ride ride) {
        return new RideResponse(
                ride.getRideId(),
                ride.getDriverId(),
                ride.getStartLocation(),
                ride.getDestination(),
                ride.getVehicleType(),
                ride.getMileageKmpl(),
                ride.getTotalDistanceKm(),
                ride.getAvailableSeats(),
                ride.getRideStatus()
        );
    }
}
