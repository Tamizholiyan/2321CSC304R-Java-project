package com.campuscommute.campuscommute.pricing.dto;

import java.math.BigDecimal;

public record PricingResponse(
        Long rideId,
        BigDecimal totalDistanceKm,
        BigDecimal mileageKmpl,
        BigDecimal ratePerLitre,
        BigDecimal totalFuelCost,
        int occupantCount,
        BigDecimal perPersonShare
) {}

