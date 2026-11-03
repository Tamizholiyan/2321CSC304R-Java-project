package com.campuscommute.campuscommute.places;

import com.campuscommute.campuscommute.auth.JwtUtil;
import com.campuscommute.campuscommute.auth.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class PlaceSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PhotonClient photonClient;

    @Autowired
    private PlaceSearchCache placeSearchCache;

    private MockRestServiceServer mockServer;
    private String validToken;

    private static final String SAMPLE_PHOTON_GEOJSON = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "coordinates": [80.1804, 13.0315],
                    "type": "Point"
                  },
                  "properties": {
                    "osm_id": 1001,
                    "name": "Easwari Engineering College",
                    "street": "Bharathi Salai",
                    "suburb": "Ramapuram",
                    "city": "Chennai",
                    "state": "Tamil Nadu",
                    "country": "India"
                  }
                },
                {
                  "type": "Feature",
                  "geometry": {
                    "coordinates": [80.1830, 13.0330],
                    "type": "Point"
                  },
                  "properties": {
                    "osm_id": 1002,
                    "name": "Ramapuram Bus Stop",
                    "street": "Mount-Poonamallee Road",
                    "district": "Ramapuram",
                    "city": "Chennai",
                    "state": "Tamil Nadu",
                    "country": "India"
                  }
                }
              ]
            }
            """;

    @BeforeEach
    void setUp() {
        placeSearchCache.clear();
        validToken = jwtUtil.generateAccessToken(1L, Role.RIDER);

        RestClient.Builder builder = RestClient.builder().baseUrl("https://photon.komoot.io");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        photonClient.setRestClient(builder.build());
    }

    @Test
    @DisplayName("Phase 9: Parsing - Successfully queries Photon and parses GeoJSON coordinates and properties")
    void testSearchPlaces_ParsingSuccess() throws Exception {
        mockServer.expect(requestTo(containsString("https://photon.komoot.io/api/?q=Easwari")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("User-Agent", containsString("CampusCommute-EEC-Carpooling")))
                .andRespond(withSuccess(SAMPLE_PHOTON_GEOJSON, MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/places/search")
                        .param("q", "Easwari")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("Easwari Engineering College")))
                .andExpect(jsonPath("$[0].address", containsString("Ramapuram")))
                .andExpect(jsonPath("$[0].lat", is(13.0315)))
                .andExpect(jsonPath("$[0].lng", is(80.1804)))
                .andExpect(jsonPath("$[1].name", is("Ramapuram Bus Stop")))
                .andExpect(jsonPath("$[1].lat", is(13.0330)))
                .andExpect(jsonPath("$[1].lng", is(80.1830)));

        mockServer.verify();
    }

    @Test
    @DisplayName("Phase 9: Short query - Queries with fewer than 3 characters return empty list without calling upstream")
    void testSearchPlaces_ShortQueryReturnsEmptyList() throws Exception {
        // Query has only 2 characters; mockServer expects zero calls
        mockMvc.perform(get("/api/places/search")
                        .param("q", "Ea")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Whitespace trimmed query < 3 characters
        mockMvc.perform(get("/api/places/search")
                        .param("q", "  hi  ")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockServer.verify();
    }

    @Test
    @DisplayName("Phase 9: Upstream failure - Returns 200 with empty list when Photon fails")
    void testSearchPlaces_UpstreamFailureReturns200WithEmptyList() throws Exception {
        mockServer.expect(requestTo(containsString("https://photon.komoot.io/api/?q=ErrorPlace")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        mockMvc.perform(get("/api/places/search")
                        .param("q", "ErrorPlace")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockServer.verify();
    }

    @Test
    @DisplayName("Phase 9: Cache hit makes only one upstream call for duplicate queries")
    void testSearchPlaces_CacheHitMakesOneUpstreamCall() throws Exception {
        // Expect only 1 call to Photon despite 2 requests
        mockServer.expect(requestTo(containsString("https://photon.komoot.io/api/?q=Tambaram")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(SAMPLE_PHOTON_GEOJSON, MediaType.APPLICATION_JSON));

        // First call - triggers upstream request
        mockMvc.perform(get("/api/places/search")
                        .param("q", "Tambaram")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Second call - served from in-memory cache
        mockMvc.perform(get("/api/places/search")
                        .param("q", "Tambaram")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockServer.verify();
    }

    @Test
    @DisplayName("Phase 9: 401 when unauthenticated")
    void testSearchPlaces_UnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/places/search")
                        .param("q", "Tambaram"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }
}
