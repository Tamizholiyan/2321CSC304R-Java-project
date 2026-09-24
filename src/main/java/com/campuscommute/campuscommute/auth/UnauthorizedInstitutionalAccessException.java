package com.campuscommute.campuscommute.auth;

public class UnauthorizedInstitutionalAccessException extends RuntimeException {

    public UnauthorizedInstitutionalAccessException(String registerNumber) {
        super("Institutional access verification failed: Register number '" + registerNumber 
                + "' is not found in Easwari Engineering College student directory.");
    }

    public UnauthorizedInstitutionalAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
