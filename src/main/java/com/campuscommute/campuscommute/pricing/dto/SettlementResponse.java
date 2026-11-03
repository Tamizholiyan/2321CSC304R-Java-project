package com.campuscommute.campuscommute.pricing.dto;

import java.math.BigDecimal;

public record SettlementResponse(
        Long rideId,
        Long driverId,
        String driverPhoneNumber,
        BigDecimal totalDistanceKm,
        BigDecimal mileageKmpl,
        BigDecimal ratePerLitre,
        BigDecimal totalFuelCost,
        int occupantCount,
        BigDecimal perPersonShare,
        String upiDeepLink
) {}

