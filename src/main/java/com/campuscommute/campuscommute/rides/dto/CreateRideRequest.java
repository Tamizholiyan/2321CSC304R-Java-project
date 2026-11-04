package com.campuscommute.campuscommute.rides.dto;

import com.campuscommute.campuscommute.rides.Ride;
import com.campuscommute.campuscommute.rides.VehicleType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateRideRequest(
        @NotBlank(message = "Start location is required")
        String startLocation,

        String destination,

        @NotNull(message = "Vehicle type is required")
        VehicleType vehicleType,

        @NotBlank(message = "Vehicle model is required")
        String vehicleModel,

        @NotBlank(message = "Vehicle registration plate is required")
        String vehiclePlate,

        @NotNull(message = "Departure time is required")
        @Future(message = "Departure time must be in the future")
        LocalDateTime departureTime,

        @NotNull(message = "Pickup latitude is required")
        @DecimalMin(value = "-90.0", message = "Pickup latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Pickup latitude must be between -90 and 90")
        Double pickupLat,

        @NotNull(message = "Pickup longitude is required")
        @DecimalMin(value = "-180.0", message = "Pickup longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Pickup longitude must be between -180 and 180")
        Double pickupLng,

        @NotNull(message = "Drop latitude is required")
        @DecimalMin(value = "-90.0", message = "Drop latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Drop latitude must be between -90 and 90")
        Double dropLat,

        @NotNull(message = "Drop longitude is required")
        @DecimalMin(value = "-180.0", message = "Drop longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Drop longitude must be between -180 and 180")
        Double dropLng,

        @NotNull(message = "Mileage is required")
        @DecimalMin(value = "5.0", message = "Mileage must be at least 5 km/l")
        @DecimalMax(value = "120.0", message = "Mileage must not exceed 120 km/l")
        BigDecimal mileageKmpl,

        @NotNull(message = "Available seats is required")
        @Min(value = 1, message = "Available seats must be between 1 and 6")
        @Max(value = 6, message = "Available seats must be between 1 and 6")
        Integer availableSeats,

        String notes
) {
    public CreateRideRequest {
        if (startLocation != null) startLocation = startLocation.trim();
        if (vehicleModel != null) vehicleModel = vehicleModel.trim();
        if (vehiclePlate != null) vehiclePlate = vehiclePlate.trim();
        if (notes != null) notes = notes.trim();
        if (destination == null || destination.isBlank()) {
            destination = Ride.EEC_NAME;
        } else {
            destination = destination.trim();
        }
    }

    /**
     * Backward-compatible convenience constructor for tests with default drop coordinates at EEC.
     */
    public CreateRideRequest(
            String startLocation,
            String destination,
            VehicleType vehicleType,
            String vehicleModel,
            String vehiclePlate,
            LocalDateTime departureTime,
            Double pickupLat,
            Double pickupLng,
            BigDecimal mileageKmpl,
            Integer availableSeats,
            String notes
    ) {
        this(
                startLocation,
                destination,
                vehicleType,
                vehicleModel,
                vehiclePlate,
                departureTime,
                pickupLat,
                pickupLng,
                Ride.EEC_LAT,
                Ride.EEC_LNG,
                mileageKmpl,
                availableSeats,
                notes
        );
    }

    /**
     * Backward-compatible convenience constructor for tests with default vehicle & coordinate attributes.
     */
    public CreateRideRequest(
            String startLocation,
            String destination,
            VehicleType vehicleType,
            BigDecimal mileageKmpl,
            Integer availableSeats
    ) {
        this(
                startLocation,
                destination,
                vehicleType,
                "Swift Dzire",
                "TN09CC1234",
                LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).plusHours(2),
                13.0359,
                80.1568,
                Ride.EEC_LAT,
                Ride.EEC_LNG,
                mileageKmpl,
                availableSeats,
                null
        );
    }
}
