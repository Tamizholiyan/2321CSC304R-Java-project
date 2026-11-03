package com.campuscommute.campuscommute.rides;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

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
     * Computes route distance with coordinates, calling Google Routes API if key configured,
     * falling back to Haversine straight-line distance * 1.25 as per Decision D4.
     */
    public BigDecimal computeDistanceKm(
            Double startLat, Double startLng,
            Double destLat, Double destLng,
            String startLocation, String destination,
            VehicleType vehicleType
    ) {
        if (!apiKey.isBlank()) {
            try {
                String travelMode = (vehicleType == VehicleType.TWO_WHEELER) ? "TWO_WHEELER" : "DRIVE";
                ComputeRoutesRequest request = new ComputeRoutesRequest(
                        new Waypoint(startLocation != null && !startLocation.isBlank() ? startLocation : startLat + "," + startLng),
                        new Waypoint(destination != null && !destination.isBlank() ? destination : destLat + "," + destLng),
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
                    if (route.distanceMeters() != null && route.distanceMeters() > 0) {
                        return BigDecimal.valueOf(route.distanceMeters())
                                .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);
                    }
                }
            } catch (Exception ex) {
                log.warn("Google Routes API failed: {}. Falling back to Haversine x 1.25", ex.getMessage());
            }
        }

        // Decision D4: straight-line (haversine) x 1.25 fallback
        if (startLat != null && startLng != null && destLat != null && destLng != null) {
            return computeHaversineDistanceKm(startLat, startLng, destLat, destLng);
        }

        return BigDecimal.ZERO;
    }

    /**
     * Backward-compatible overload for address-only calculation.
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
            return BigDecimal.ZERO;
        } catch (Exception ex) {
            log.error("Failed to compute routes via Google Routes API: {}", ex.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Google Routes service unavailable: " + ex.getMessage(),
                    ex
            );
        }
    }

    /**
     * Haversine formula calculation for road distance estimation (straight line * 1.25).
     */
    public static BigDecimal computeHaversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        final double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double straightLineKm = earthRadiusKm * c;
        double roadDistanceKm = straightLineKm * 1.25; // D4: Haversine x 1.25
        return BigDecimal.valueOf(roadDistanceKm).setScale(2, RoundingMode.HALF_UP);
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
