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
        Double dropLat,
        Double dropLng,
        String notes,
        BigDecimal mileageKmpl,
        BigDecimal totalDistanceKm,
        Integer availableSeats,
        RideStatus rideStatus,
        Double pickupDistanceKm,
        String conflictMessage
) {
    public static RideResponse from(Ride ride, String driverName, String department, Double pickupDistanceKm, String conflictMessage) {
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
                ride.getDropLat(),
                ride.getDropLng(),
                ride.getNotes(),
                ride.getMileageKmpl(),
                ride.getTotalDistanceKm(),
                ride.getAvailableSeats(),
                ride.getRideStatus(),
                pickupDistanceKm,
                conflictMessage
        );
    }

    public static RideResponse from(Ride ride, String driverName, String department, Double pickupDistanceKm) {
        return from(ride, driverName, department, pickupDistanceKm, null);
    }

    public static RideResponse from(Ride ride, String driverName, String department) {
        return from(ride, driverName, department, null, null);
    }

    public static RideResponse from(Ride ride) {
        return from(ride, null, null, null, null);
    }
}
