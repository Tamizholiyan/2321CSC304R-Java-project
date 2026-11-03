package com.campuscommute.campuscommute.rides.dto;

import com.campuscommute.campuscommute.rides.Ride;
import com.campuscommute.campuscommute.rides.RideStatus;
import com.campuscommute.campuscommute.rides.VehicleType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RideResponse(
        Long rideId,
        Long driverId,
        String driverName,
        String department,
        String startLocation,
        String destination,
        VehicleType vehicleType,
        String vehicleModel,
        String vehiclePlate,
        LocalDateTime departureTime,
        Double pickupLat,
        Double pickupLng,
        String notes,
        BigDecimal mileageKmpl,
        BigDecimal totalDistanceKm,
        Integer availableSeats,
        RideStatus rideStatus
) {
    public static RideResponse from(Ride ride, String driverName, String department) {
        return new RideResponse(
                ride.getRideId(),
                ride.getDriverId(),
                driverName,
                department,
                ride.getStartLocation(),
                ride.getDestination(),
                ride.getVehicleType(),
                ride.getVehicleModel(),
                ride.getVehiclePlate(),
                ride.getDepartureTime(),
                ride.getPickupLat(),
                ride.getPickupLng(),
                ride.getNotes(),
                ride.getMileageKmpl(),
                ride.getTotalDistanceKm(),
                ride.getAvailableSeats(),
                ride.getRideStatus()
        );
    }

    public static RideResponse from(Ride ride) {
        return from(ride, null, null);
    }
}
