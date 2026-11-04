package com.campuscommute.campuscommute.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Register number is required")
        @Pattern(regexp = "^[0-9]{12}$", message = "Register number must be exactly 12 digits")
        String registerNumber,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Phone number must be a valid 10-digit Indian mobile number")
        String phoneNumber,

        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        String password,

        String email
) {
    public RegisterRequest(String registerNumber, String phoneNumber, String password) {
        this(registerNumber, phoneNumber, password, null);
    }

    public RegisterRequest {
        if (registerNumber != null) {
            registerNumber = registerNumber.trim();
        }
        if (phoneNumber != null) {
            phoneNumber = phoneNumber.trim();
        }
        if (email != null) {
            email = email.trim();
        }
    }
}
