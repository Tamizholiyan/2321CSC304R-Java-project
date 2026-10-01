package com.campuscommute.campuscommute.rides.dto;

import com.campuscommute.campuscommute.rides.RideStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateRideStatusRequest(
        @NotNull(message = "Ride status is required")
        RideStatus status
) {}
