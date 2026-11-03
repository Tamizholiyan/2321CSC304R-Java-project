package com.campuscommute.campuscommute.pricing;

import java.math.BigDecimal;

public interface FuelRateProvider {
    BigDecimal getCurrentRatePerLitre();
}

