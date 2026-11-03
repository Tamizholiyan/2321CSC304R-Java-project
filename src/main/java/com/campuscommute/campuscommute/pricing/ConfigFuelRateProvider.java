package com.campuscommute.campuscommute.pricing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ConfigFuelRateProvider implements FuelRateProvider {

    private final BigDecimal ratePerLitre;

    public ConfigFuelRateProvider(
            @Value("${app.pricing.petrol-rate-per-litre:102.50}") BigDecimal ratePerLitre
    ) {
        this.ratePerLitre = ratePerLitre;
    }

    @Override
    public BigDecimal getCurrentRatePerLitre() {
        return ratePerLitre;
    }
}

