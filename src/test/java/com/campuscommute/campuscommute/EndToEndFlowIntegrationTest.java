package com.campuscommute.campuscommute;

import com.campuscommute.campuscommute.auth.UserRepository;
import com.campuscommute.campuscommute.auth.dto.AuthResponse;
import com.campuscommute.campuscommute.auth.dto.RegisterRequest;
import com.campuscommute.campuscommute.auth.dto.UpdateRoleRequest;
import com.campuscommute.campuscommute.auth.Role;
import com.campuscommute.campuscommute.bookings.BookingRepository;
import com.campuscommute.campuscommute.rides.GoogleRoutesClient;
import com.campuscommute.campuscommute.rides.RideRepository;
import com.campuscommute.campuscommute.rides.RideStatus;
import com.campuscommute.campuscommute.rides.VehicleType;
import com.campuscommute.campuscommute.rides.dto.CreateRideRequest;
import com.campuscommute.campuscommute.rides.dto.RideResponse;
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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class EndToEndFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private GoogleRoutesClient googleRoutesClient;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        rideRepository.deleteAll();
        userRepository.deleteAll();

        when(googleRoutesClient.computeDistanceKm(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new BigDecimal("20.00"));
    }

    @Test
    @DisplayName("Full End-to-End lifecycle: Register -> Role Update -> Create Ride -> Book Seat -> Start -> Complete -> Pricing & Settlement")
    void fullEndToEndLifecycle() throws Exception {
        // 1. Register Driver (Student: 310621104001)
        RegisterRequest driverReg = new RegisterRequest("310621104001", "9876543210", "Password@123");
        MvcResult driverRegResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(driverReg)))
                .andExpect(status().isCreated())
                .andReturn();
        AuthResponse driverAuth = objectMapper.readValue(driverRegResult.getResponse().getContentAsString(), AuthResponse.class);

        // Update Driver role to DRIVER
        mockMvc.perform(patch("/api/users/me/role")
                        .header("Authorization", "Bearer " + driverAuth.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest(Role.DRIVER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("DRIVER")));

        // Refresh token to get updated JWT claims reflecting ROLE_DRIVER
        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.campuscommute.campuscommute.auth.dto.RefreshTokenRequest(driverAuth.refreshToken()))))
                .andExpect(status().isOk())
                .andReturn();
        driverAuth = objectMapper.readValue(refreshResult.getResponse().getContentAsString(), AuthResponse.class);

        // 2. Register Passenger (Student: 310621104002) - defaults to RIDER
        RegisterRequest riderReg = new RegisterRequest("310621104002", "9876543211", "Password@123");
        MvcResult riderRegResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(riderReg)))
                .andExpect(status().isCreated())
                .andReturn();
        AuthResponse riderAuth = objectMapper.readValue(riderRegResult.getResponse().getContentAsString(), AuthResponse.class);

        // 3. Driver creates a ride
        CreateRideRequest rideReq = new CreateRideRequest(
                "Tambaram Sanatorium",
                "Easwari Engineering College",
                VehicleType.FOUR_WHEELER,
                new BigDecimal("15.00"),
                3
        );
        MvcResult createRideResult = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + driverAuth.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rideReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rideStatus", is("SCHEDULED")))
                .andExpect(jsonPath("$.availableSeats", is(3)))
                .andReturn();
        RideResponse createdRide = objectMapper.readValue(createRideResult.getResponse().getContentAsString(), RideResponse.class);
        Long rideId = createdRide.rideId();

        // 4. Rider books a seat on this ride
        mockMvc.perform(post("/api/bookings?rideId=" + rideId)
                        .header("Authorization", "Bearer " + riderAuth.accessToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingStatus", is("CONFIRMED")));

        // Verify available seats decreased to 2
        mockMvc.perform(get("/api/rides/" + rideId)
                        .header("Authorization", "Bearer " + riderAuth.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableSeats", is(2)));

        // 5. Pricing before completion must return 409 Conflict
        mockMvc.perform(get("/api/rides/" + rideId + "/pricing")
                        .header("Authorization", "Bearer " + riderAuth.accessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));

        // 6. Driver starts the ride: SCHEDULED -> ONGOING
        mockMvc.perform(patch("/api/rides/" + rideId + "/status")
                        .header("Authorization", "Bearer " + driverAuth.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRideStatusRequest(RideStatus.ONGOING))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rideStatus", is("ONGOING")));

        // 7. Driver finishes the ride: ONGOING -> COMPLETED
        mockMvc.perform(patch("/api/rides/" + rideId + "/status")
                        .header("Authorization", "Bearer " + driverAuth.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRideStatusRequest(RideStatus.COMPLETED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rideStatus", is("COMPLETED")));

        // 8. Dynamic Pricing check:
        // Distance = 20.00 km, Mileage = 15.00 kmpl, Rate = 102.50 INR
        // Total fuel cost = (20 / 15) * 102.50 = 136.67 INR
        // Occupants = 1 driver + 1 rider = 2 occupants
        // Per-person share = 136.67 / 2 = 68.34 INR
        mockMvc.perform(get("/api/rides/" + rideId + "/pricing")
                        .header("Authorization", "Bearer " + riderAuth.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupantCount", is(2)))
                .andExpect(jsonPath("$.totalFuelCost", is(136.67)))
                .andExpect(jsonPath("$.perPersonShare", is(68.34)));

        // 9. Peer-to-Peer Settlement check:
        mockMvc.perform(get("/api/rides/" + rideId + "/settlement")
                        .header("Authorization", "Bearer " + riderAuth.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverPhoneNumber", is("9876543210")))
                .andExpect(jsonPath("$.perPersonShare", is(68.34)))
                .andExpect(jsonPath("$.upiDeepLink", containsString("upi://pay?pa=9876543210@upi")))
                .andExpect(jsonPath("$.upiDeepLink", containsString("am=68.34")));
    }
}
