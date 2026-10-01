package com.campuscommute.campuscommute.rides.dto;

import com.campuscommute.campuscommute.rides.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateRideRequest(
        @NotBlank(message = "Start location is required")
        String startLocation,

        @NotBlank(message = "Destination is required")
        String destination,

        @NotNull(message = "Vehicle type is required")
        VehicleType vehicleType,

        @NotNull(message = "Mileage is required")
        @Positive(message = "Mileage must be positive")
        BigDecimal mileageKmpl,

        @NotNull(message = "Available seats is required")
        @Positive(message = "Available seats must be greater than zero")
        Integer availableSeats
) {}
