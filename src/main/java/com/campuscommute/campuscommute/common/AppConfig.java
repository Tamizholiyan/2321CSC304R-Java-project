package com.campuscommute.campuscommute.common;

import jakarta.validation.ClockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Global application configuration.
 * Configures the application-wide Clock enforcing Asia/Kolkata (IST, UTC+5:30) timezone
 * and provides a ClockProvider for Bean Validation (@Future annotations).
 */
@Configuration
public class AppConfig {

    public static final ZoneId KOLKATA_ZONE = ZoneId.of("Asia/Kolkata");

    @Bean
    public Clock clock() {
        return Clock.system(KOLKATA_ZONE);
    }

    @Bean
    public ClockProvider clockProvider(Clock clock) {
        return () -> clock;
    }
}
