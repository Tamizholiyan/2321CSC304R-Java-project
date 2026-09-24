package com.campuscommute.campuscommute.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Register number is required")
        @Size(max = 12, message = "Register number must not exceed 12 characters")
        String registerNumber,

        @NotBlank(message = "Phone number is required")
        @Size(min = 10, max = 15, message = "Phone number must be between 10 and 15 digits")
        String phoneNumber,

        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        String password
) {}
