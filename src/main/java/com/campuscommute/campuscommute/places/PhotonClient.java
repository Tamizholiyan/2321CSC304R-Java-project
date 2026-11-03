package com.campuscommute.campuscommute.places;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class PhotonClient {

    private static final Logger log = LoggerFactory.getLogger(PhotonClient.class);
    private static final String DEFAULT_BASE_URL = "https://photon.komoot.io";
    private static final double DEFAULT_CHENNAI_LAT = 13.0827;
    private static final double DEFAULT_CHENNAI_LNG = 80.2707;
    private static final String CHENNAI_BBOX = "79.9,12.8,80.4,13.3";
    private static final String USER_AGENT = "CampusCommute-EEC-Carpooling/1.0 (contact: commute@eec.srmrmp.edu.in)";

    private RestClient restClient;

    public PhotonClient(RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));

        RestClient.Builder builder = (restClientBuilder != null) ? restClientBuilder : RestClient.builder();
        this.restClient = builder
                .baseUrl(DEFAULT_BASE_URL)
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", USER_AGENT)
                .build();
    }

    public void setRestClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public List<PlaceSuggestion> search(String query, Double lat, Double lng) {
        if (query == null || query.trim().length() < 3) {
            return Collections.emptyList();
        }

        double searchLat = (lat != null) ? lat : DEFAULT_CHENNAI_LAT;
        double searchLng = (lng != null) ? lng : DEFAULT_CHENNAI_LNG;

        try {
            PhotonResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/")
                            .queryParam("q", query.trim())
                            .queryParam("limit", 6)
                            .queryParam("lang", "en")
                            .queryParam("lat", searchLat)
                            .queryParam("lon", searchLng)
                            .queryParam("bbox", CHENNAI_BBOX)
                            .build())
                    .header("User-Agent", USER_AGENT)
                    .retrieve()
                    .body(PhotonResponse.class);

            if (response == null || response.features() == null || response.features().isEmpty()) {
                return Collections.emptyList();
            }

            List<PlaceSuggestion> results = new ArrayList<>();
            Set<String> seenKeys = new HashSet<>();

            for (PhotonResponse.Feature feature : response.features()) {
                if (feature == null || feature.geometry() == null || feature.geometry().coordinates() == null) {
                    continue;
                }
                List<Double> coords = feature.geometry().coordinates();
                if (coords.size() < 2) {
                    continue;
                }
                // Photon returns GeoJSON [lng, lat]
                Double placeLng = coords.get(0);
                Double placeLat = coords.get(1);

                PhotonResponse.Properties props = feature.properties();
                String placeName = extractName(props);
                String address = buildAddress(props, placeName);

                // Deduplicate by name and approximate coordinates
                String dedupeKey = placeName.toLowerCase() + "|" + Math.round(placeLat * 1000) + "|" + Math.round(placeLng * 1000);
                if (seenKeys.add(dedupeKey)) {
                    results.add(new PlaceSuggestion(placeName, address, placeLat, placeLng));
                    if (results.size() >= 6) {
                        break;
                    }
                }
            }

            return results;
        } catch (Exception ex) {
            // If Photon fails or times out: log it and return 200 with an empty list
            log.warn("Photon place search failed or timed out for query '{}': {}", query, ex.getMessage());
            return Collections.emptyList();
        }
    }

    private String extractName(PhotonResponse.Properties props) {
        if (props == null) return "Location";
        if (props.name() != null && !props.name().isBlank()) {
            return props.name().trim();
        }
        if (props.street() != null && !props.street().isBlank()) {
            return props.street().trim();
        }
        if (props.locality() != null && !props.locality().isBlank()) {
            return props.locality().trim();
        }
        if (props.district() != null && !props.district().isBlank()) {
            return props.district().trim();
        }
        return "Location";
    }

    private String buildAddress(PhotonResponse.Properties props, String placeName) {
        if (props == null) return "";
        List<String> parts = new ArrayList<>();

        if (props.housenumber() != null && !props.housenumber().isBlank()
                && props.street() != null && !props.street().isBlank()) {
            parts.add(props.housenumber().trim() + " " + props.street().trim());
        } else if (props.street() != null && !props.street().isBlank() && !props.street().equalsIgnoreCase(placeName)) {
            parts.add(props.street().trim());
        }

        if (props.suburb() != null && !props.suburb().isBlank() && !props.suburb().equalsIgnoreCase(placeName)) {
            parts.add(props.suburb().trim());
        } else if (props.district() != null && !props.district().isBlank() && !props.district().equalsIgnoreCase(placeName)) {
            parts.add(props.district().trim());
        }

        if (props.locality() != null && !props.locality().isBlank()
                && !props.locality().equalsIgnoreCase(placeName)
                && !parts.contains(props.locality().trim())) {
            parts.add(props.locality().trim());
        }

        if (props.city() != null && !props.city().isBlank()) {
            parts.add(props.city().trim());
        }

        if (props.state() != null && !props.state().isBlank()) {
            parts.add(props.state().trim());
        }

        if (parts.isEmpty()) {
            return placeName;
        }

        return String.join(", ", parts);
    }
}
