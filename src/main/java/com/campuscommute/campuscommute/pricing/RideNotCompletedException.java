package com.campuscommute.campuscommute.pricing;

public class RideNotCompletedException extends RuntimeException {
    public RideNotCompletedException(String message) {
        super(message);
    }
}

