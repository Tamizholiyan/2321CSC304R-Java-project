package com.campuscommute.campuscommute.rides;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class GoogleRoutesClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleRoutesClient.class);
    private static final String COMPUTE_ROUTES_PATH = "/directions/v2:computeRoutes";

    private final RestClient restClient;
    private final String apiKey;

    @org.springframework.beans.factory.annotation.Autowired
    public GoogleRoutesClient(
            @Value("${app.google.routes.api-key:}") String apiKey,
            RestClient.Builder restClientBuilder
    ) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        RestClient.Builder builder = (restClientBuilder != null) ? restClientBuilder : RestClient.builder();
        this.restClient = builder
                .baseUrl("https://routes.googleapis.com")
                .build();
    }

    public GoogleRoutesClient(String apiKey) {
        this(apiKey, RestClient.builder());
    }

    /**
     * Calls Google Routes API (v2 computeRoutes) to calculate distance between startLocation and destination.
     *
     * @param startLocation the origin free-text address
     * @param destination the destination free-text address
     * @param vehicleType the vehicle type (TWO_WHEELER or FOUR_WHEELER)
     * @return calculated distance in kilometers, rounded to 2 decimal places
     */
    public BigDecimal computeDistanceKm(String startLocation, String destination, VehicleType vehicleType) {
        if (apiKey.isBlank()) {
            log.warn("app.google.routes.api-key is not configured. Falling back to BigDecimal.ZERO.");
            return BigDecimal.ZERO;
        }

        try {
            String travelMode = (vehicleType == VehicleType.TWO_WHEELER) ? "TWO_WHEELER" : "DRIVE";
            ComputeRoutesRequest request = new ComputeRoutesRequest(
                    new Waypoint(startLocation),
                    new Waypoint(destination),
                    travelMode
            );

            ComputeRoutesResponse response = restClient.post()
                    .uri(COMPUTE_ROUTES_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", "routes.distanceMeters")
                    .body(request)
                    .retrieve()
                    .body(ComputeRoutesResponse.class);

            if (response != null && response.routes() != null && !response.routes().isEmpty()) {
                Route route = response.routes().get(0);
                if (route.distanceMeters() != null) {
                    return BigDecimal.valueOf(route.distanceMeters())
                            .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);
                }
            }

            log.warn("No route distance returned from Google Routes API for {} -> {}", startLocation, destination);
            return BigDecimal.ZERO;
        } catch (Exception ex) {
            log.error("Failed to compute routes via Google Routes API: {}", ex.getMessage(), ex);
            throw new RuntimeException("Google Routes API call failed: " + ex.getMessage(), ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ComputeRoutesRequest(
            Waypoint origin,
            Waypoint destination,
            String travelMode
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Waypoint(
            String address
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ComputeRoutesResponse(
            List<Route> routes
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Route(
            Integer distanceMeters
    ) {}
}
