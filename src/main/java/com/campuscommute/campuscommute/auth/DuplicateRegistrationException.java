package com.campuscommute.campuscommute.auth;

public class DuplicateRegistrationException extends RuntimeException {

    public DuplicateRegistrationException(String message) {
        super(message);
    }

    public static DuplicateRegistrationException forRegisterNumber(String registerNumber) {
        return new DuplicateRegistrationException("Duplicate registration: An account with register number '" 
                + registerNumber + "' already exists.");
    }

    public static DuplicateRegistrationException forPhoneNumber(String phoneNumber) {
        return new DuplicateRegistrationException("Duplicate registration: An account with phone number '" 
                + phoneNumber + "' already exists.");
    }
}
