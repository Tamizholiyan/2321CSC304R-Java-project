package com.campuscommute.campuscommute.pricing;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI campusCommuteOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Campus Commute API")
                        .description("Peer-to-peer carpooling & bike-pooling platform for Easwari Engineering College, Chennai. "
                                + "Provides Walled-Garden Student Auth, Ride Lifecycle Management, Concurrency-Safe Seat Reservations, "
                                + "Dynamic Post-Ride Fuel Expense Splitting, and Real-Time Driver GPS Tracking.")
                        .version("1.0.0")
                        .contact(new Contact().name("Easwari Engineering College - Campus Commute Team")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}

