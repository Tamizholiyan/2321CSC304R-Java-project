package com.campuscommute.campuscommute.rides;

public class InvalidRideStateException extends RuntimeException {
    public InvalidRideStateException(String message) {
        super(message);
    }
}
