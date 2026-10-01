package com.campuscommute.campuscommute.rides;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleRoutesClientTest {

    private GoogleRoutesClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GoogleRoutesClient("test-api-key", builder);
    }

    @Test
    @DisplayName("Successfully computes distance from Google Routes API response")
    void testComputeDistanceKmSuccess() {
        String jsonResponse = """
                {
                  "routes": [
                    {
                      "distanceMeters": 15420
                    }
                  ]
                }
                """;

        server.expect(requestTo("https://routes.googleapis.com/directions/v2:computeRoutes"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "test-api-key"))
                .andExpect(header("X-Goog-FieldMask", "routes.distanceMeters"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        BigDecimal distance = client.computeDistanceKm("Easwari Engineering College", "Chennai Central", VehicleType.FOUR_WHEELER);

        assertThat(distance).isEqualByComparingTo(new BigDecimal("15.42"));
        server.verify();
    }

    @Test
    @DisplayName("Returns ZERO if API key is blank")
    void testComputeDistanceKmBlankApiKey() {
        GoogleRoutesClient clientWithoutKey = new GoogleRoutesClient("", RestClient.builder());
        BigDecimal distance = clientWithoutKey.computeDistanceKm("Origin", "Destination", VehicleType.TWO_WHEELER);
        assertThat(distance).isEqualTo(BigDecimal.ZERO);
    }
}
