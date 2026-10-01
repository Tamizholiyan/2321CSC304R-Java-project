package com.campuscommute.campuscommute.rides;

import com.campuscommute.campuscommute.auth.JwtUtil;
import com.campuscommute.campuscommute.auth.Role;
import com.campuscommute.campuscommute.rides.dto.CreateRideRequest;
import com.campuscommute.campuscommute.rides.dto.UpdateRideStatusRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class RideIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private GoogleRoutesClient googleRoutesClient;

    private String driverToken;
    private String riderToken;

    @BeforeEach
    void setUp() {
        rideRepository.deleteAll();

        driverToken = jwtUtil.generateAccessToken(100L, Role.DRIVER);
        riderToken = jwtUtil.generateAccessToken(200L, Role.RIDER);

        when(googleRoutesClient.computeDistanceKm(anyString(), anyString(), any(VehicleType.class)))
                .thenReturn(new BigDecimal("15.75"));
    }

    @Test
    @DisplayName("AC1: Non-driver gets 403 Forbidden on POST /api/rides")
    void testNonDriverGetsForbiddenOnPostRides() throws Exception {
        CreateRideRequest request = new CreateRideRequest(
                "Easwari Engineering College, Ramapuram",
                "Chennai Central Railway Station",
                VehicleType.FOUR_WHEELER,
                new BigDecimal("18.50"),
                3
        );

        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + riderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("AC2 & AC5: Creating a ride sets status SCHEDULED, seats requested, and server-side computed distance")
    void testCreateRideSuccess() throws Exception {
        CreateRideRequest request = new CreateRideRequest(
                "Easwari Engineering College, Ramapuram",
                "Guindy Metro Station",
                VehicleType.TWO_WHEELER,
                new BigDecimal("45.00"),
                1
        );

        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rideId").isNumber())
                .andExpect(jsonPath("$.driverId", is(100)))
                .andExpect(jsonPath("$.startLocation", is("Easwari Engineering College, Ramapuram")))
                .andExpect(jsonPath("$.destination", is("Guindy Metro Station")))
                .andExpect(jsonPath("$.vehicleType", is("TWO_WHEELER")))
                .andExpect(jsonPath("$.mileageKmpl", is(45.00)))
                .andExpect(jsonPath("$.availableSeats", is(1)))
                .andExpect(jsonPath("$.rideStatus", is("SCHEDULED")))
                .andExpect(jsonPath("$.totalDistanceKm", is(15.75)));

        assertThat(rideRepository.findAll()).hasSize(1);
        Ride saved = rideRepository.findAll().get(0);
        assertThat(saved.getRideStatus()).isEqualTo(RideStatus.SCHEDULED);
        assertThat(saved.getAvailableSeats()).isEqualTo(1);
        assertThat(saved.getTotalDistanceKm()).isEqualByComparingTo(new BigDecimal("15.75"));
    }

    @Test
    @DisplayName("AC3: PATCH /api/rides/{id}/status rejects illegal transitions with 409 Conflict")
    void testPatchStatusRejectsIllegalTransitions() throws Exception {
        Ride ride = Ride.builder()
                .driverId(100L)
                .startLocation("Easwari")
                .destination("T Nagar")
                .vehicleType(VehicleType.FOUR_WHEELER)
                .mileageKmpl(new BigDecimal("15.00"))
                .totalDistanceKm(new BigDecimal("12.00"))
                .availableSeats(4)
                .rideStatus(RideStatus.SCHEDULED)
                .build();
        ride = rideRepository.save(ride);

        // Attempt SCHEDULED -> COMPLETED directly (illegal)
        UpdateRideStatusRequest illegalRequest = new UpdateRideStatusRequest(RideStatus.COMPLETED);
        mockMvc.perform(patch("/api/rides/" + ride.getRideId() + "/status")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(illegalRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Illegal status transition")));

        // Legal transition: SCHEDULED -> ONGOING
        UpdateRideStatusRequest legalOngoing = new UpdateRideStatusRequest(RideStatus.ONGOING);
        mockMvc.perform(patch("/api/rides/" + ride.getRideId() + "/status")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(legalOngoing)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rideStatus", is("ONGOING")));

        // Legal transition: ONGOING -> COMPLETED
        UpdateRideStatusRequest legalCompleted = new UpdateRideStatusRequest(RideStatus.COMPLETED);
        mockMvc.perform(patch("/api/rides/" + ride.getRideId() + "/status")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(legalCompleted)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rideStatus", is("COMPLETED")));

        // Illegal transition from COMPLETED -> SCHEDULED
        UpdateRideStatusRequest illegalAfterCompleted = new UpdateRideStatusRequest(RideStatus.SCHEDULED);
        mockMvc.perform(patch("/api/rides/" + ride.getRideId() + "/status")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(illegalAfterCompleted)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @DisplayName("AC4: DELETE /api/rides/{id} on an ONGOING ride returns 409 Conflict")
    void testDeleteOngoingRideReturns409() throws Exception {
        Ride ongoingRide = Ride.builder()
                .driverId(100L)
                .startLocation("EEC")
                .destination("Tambaram")
                .vehicleType(VehicleType.FOUR_WHEELER)
                .mileageKmpl(new BigDecimal("14.00"))
                .totalDistanceKm(new BigDecimal("20.00"))
                .availableSeats(2)
                .rideStatus(RideStatus.ONGOING)
                .build();
        ongoingRide = rideRepository.save(ongoingRide);

        mockMvc.perform(delete("/api/rides/" + ongoingRide.getRideId())
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Cannot delete ride while it is ONGOING")));

        assertThat(rideRepository.existsById(ongoingRide.getRideId())).isTrue();
    }

    @Test
    @DisplayName("DELETE /api/rides/{id} on a SCHEDULED ride returns 204 No Content")
    void testDeleteScheduledRideReturns204() throws Exception {
        Ride scheduledRide = Ride.builder()
                .driverId(100L)
                .startLocation("EEC")
                .destination("Tambaram")
                .vehicleType(VehicleType.FOUR_WHEELER)
                .mileageKmpl(new BigDecimal("14.00"))
                .totalDistanceKm(new BigDecimal("20.00"))
                .availableSeats(2)
                .rideStatus(RideStatus.SCHEDULED)
                .build();
        scheduledRide = rideRepository.save(scheduledRide);

        mockMvc.perform(delete("/api/rides/" + scheduledRide.getRideId())
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isNoContent());

        assertThat(rideRepository.existsById(scheduledRide.getRideId())).isFalse();
    }

    @Test
    @DisplayName("GET /api/rides with search filters and GET /api/rides/{id}")
    void testGetRidesAndGetById() throws Exception {
        Ride ride1 = Ride.builder()
                .driverId(100L)
                .startLocation("Easwari College")
                .destination("Vadapalani")
                .vehicleType(VehicleType.TWO_WHEELER)
                .mileageKmpl(new BigDecimal("40.00"))
                .totalDistanceKm(new BigDecimal("6.50"))
                .availableSeats(1)
                .rideStatus(RideStatus.SCHEDULED)
                .build();

        Ride ride2 = Ride.builder()
                .driverId(101L)
                .startLocation("Tambaram")
                .destination("Easwari College")
                .vehicleType(VehicleType.FOUR_WHEELER)
                .mileageKmpl(new BigDecimal("16.00"))
                .totalDistanceKm(new BigDecimal("18.00"))
                .availableSeats(3)
                .rideStatus(RideStatus.SCHEDULED)
                .build();

        ride1 = rideRepository.save(ride1);
        ride2 = rideRepository.save(ride2);

        // Get by ID
        mockMvc.perform(get("/api/rides/" + ride1.getRideId())
                        .header("Authorization", "Bearer " + riderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rideId", is(ride1.getRideId().intValue())))
                .andExpect(jsonPath("$.startLocation", is("Easwari College")));

        // Non-existent ID returns 404
        mockMvc.perform(get("/api/rides/99999")
                        .header("Authorization", "Bearer " + riderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));

        // List all rides
        mockMvc.perform(get("/api/rides")
                        .header("Authorization", "Bearer " + riderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Filter by destination
        mockMvc.perform(get("/api/rides")
                        .param("destination", "Vadapalani")
                        .header("Authorization", "Bearer " + riderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].startLocation", is("Easwari College")));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    @DisplayName("Module 3 dependency check: findByIdForUpdate locks and retrieves ride")
    void testFindByIdForUpdate() {
        Ride ride = Ride.builder()
                .driverId(100L)
                .startLocation("EEC")
                .destination("Guindy")
                .vehicleType(VehicleType.TWO_WHEELER)
                .mileageKmpl(new BigDecimal("50.00"))
                .totalDistanceKm(new BigDecimal("8.00"))
                .availableSeats(1)
                .rideStatus(RideStatus.SCHEDULED)
                .build();
        ride = rideRepository.save(ride);

        Optional<Ride> locked = rideRepository.findByIdForUpdate(ride.getRideId());
        assertThat(locked).isPresent();
        assertThat(locked.get().getRideId()).isEqualTo(ride.getRideId());
    }
}
