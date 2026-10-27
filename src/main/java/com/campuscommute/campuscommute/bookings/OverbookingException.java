package com.campuscommute.campuscommute.bookings;

public class OverbookingException extends RuntimeException {
    public OverbookingException(String message) {
        super(message);
    }
}

