package com.campuscommute.campuscommute.auth.dto;

import com.campuscommute.campuscommute.auth.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(
        @NotNull(message = "Role is required (DRIVER or RIDER)")
        Role role
) {}
