package com.campuscommute.campuscommute.rides;

public class RideNotFoundException extends RuntimeException {
    public RideNotFoundException(String message) {
        super(message);
    }

    public RideNotFoundException(Long rideId) {
        super("Ride not found with id: " + rideId);
    }
}
