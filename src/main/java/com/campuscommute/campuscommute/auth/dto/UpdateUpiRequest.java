package com.campuscommute.campuscommute.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateUpiRequest(
        @NotBlank(message = "UPI ID is required")
        @Pattern(regexp = "^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}$", message = "Invalid UPI ID format. Expected format: name@handle")
        String upiId
) {
    public UpdateUpiRequest {
        if (upiId != null) {
            upiId = upiId.trim();
        }
    }
}
