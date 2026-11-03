package com.campuscommute.campuscommute.rides;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class RideOverlapException extends RuntimeException {
    public RideOverlapException(String message) {
        super(message);
    }
}

