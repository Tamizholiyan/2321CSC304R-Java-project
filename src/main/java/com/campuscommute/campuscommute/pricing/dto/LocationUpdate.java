package com.campuscommute.campuscommute.pricing.dto;

public record LocationUpdate(
        Double latitude,
        Double longitude,
        Double speed,
        Double heading,
        Long timestamp
) {}

