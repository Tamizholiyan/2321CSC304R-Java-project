package com.campuscommute.campuscommute.pricing;

import com.campuscommute.campuscommute.auth.JwtUtil;
import com.campuscommute.campuscommute.auth.Role;
import com.campuscommute.campuscommute.auth.User;
import com.campuscommute.campuscommute.auth.UserRepository;
import com.campuscommute.campuscommute.bookings.Booking;
import com.campuscommute.campuscommute.bookings.BookingRepository;
import com.campuscommute.campuscommute.bookings.BookingStatus;
import com.campuscommute.campuscommute.rides.Ride;
import com.campuscommute.campuscommute.rides.RideRepository;
import com.campuscommute.campuscommute.rides.RideStatus;
import com.campuscommute.campuscommute.rides.VehicleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class PricingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private String userToken;
    private User driver;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        rideRepository.deleteAll();

        driver = userRepository.findByPhoneNumber("9876543210").orElseGet(() -> {
            User newUser = new User("310621104001", "9876543210", "$2a$10$dummyHash", Role.DRIVER);
            return userRepository.save(newUser);
        });

        userToken = jwtUtil.generateAccessToken(driver.getUserId(), Role.DRIVER);
    }

    @Test
    @DisplayName("GET /api/rides/{id}/pricing on non-existent ride returns 404")
    void getPricing_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/rides/99999/pricing")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")));
    }

    @Test
    @DisplayName("GET /api/rides/{id}/pricing on non-COMPLETED ride returns 409 Conflict")
    void getPricing_NonCompletedRide_Returns409() throws Exception {
        Ride scheduledRide = Ride.builder()
                .driverId(driver.getUserId())
                .startLocation("Porur Roundana")
                .destination("Easwari Engineering College")
                .vehicleType(VehicleType.FOUR_WHEELER)
                .mileageKmpl(new BigDecimal("15.00"))
                .totalDistanceKm(new BigDecimal("30.00"))
                .availableSeats(3)
                .rideStatus(RideStatus.SCHEDULED)
                .build();
        Ride saved = rideRepository.save(scheduledRide);

        mockMvc.perform(get("/api/rides/" + saved.getRideId() + "/pricing")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("is not COMPLETED yet")));
    }

    @Test
    @DisplayName("GET /api/rides/{id}/pricing on COMPLETED ride with 1 driver + 2 confirmed riders divides cost by 3")
    void getPricing_CompletedRide_DividesCostByThree() throws Exception {
        // Distance = 30 km, Mileage = 15 km/l -> 2 litres
        // Rate per litre = 102.50 -> Total Fuel Cost = 205.00
        // Occupants = 1 driver + 2 riders = 3
        // Per person share = 205.00 / 3 = 68.33
        Ride completedRide = Ride.builder()
                .driverId(driver.getUserId())
                .startLocation("Guindy")
                .destination("Easwari Engineering College")
                .vehicleType(VehicleType.FOUR_WHEELER)
                .mileageKmpl(new BigDecimal("15.00"))
                .totalDistanceKm(new BigDecimal("30.00"))
                .availableSeats(1)
                .rideStatus(RideStatus.COMPLETED)
                .build();
        Ride savedRide = rideRepository.save(completedRide);

        // Add 2 confirmed bookings
        Booking b1 = Booking.builder()
                .rideId(savedRide.getRideId())
                .riderId(101L)
                .bookingTime(LocalDateTime.now())
                .bookingStatus(BookingStatus.CONFIRMED)
                .build();
        Booking b2 = Booking.builder()
                .rideId(savedRide.getRideId())
                .riderId(102L)
                .bookingTime(LocalDateTime.now())
                .bookingStatus(BookingStatus.CONFIRMED)
                .build();
        bookingRepository.save(b1);
        bookingRepository.save(b2);

        mockMvc.perform(get("/api/rides/" + savedRide.getRideId() + "/pricing")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rideId", is(savedRide.getRideId().intValue())))
                .andExpect(jsonPath("$.occupantCount", is(3)))
                .andExpect(jsonPath("$.totalFuelCost", is(205.00)))
                .andExpect(jsonPath("$.perPersonShare", is(68.33)));
    }

    @Test
    @DisplayName("GET /api/rides/{id}/settlement returns pricing breakdown and UPI deep-link string")
    void getSettlement_CompletedRide_ReturnsUpiDeepLink() throws Exception {
        Ride completedRide = Ride.builder()
                .driverId(driver.getUserId())
                .startLocation("Tambaram")
                .destination("Easwari Engineering College")
                .vehicleType(VehicleType.TWO_WHEELER)
                .mileageKmpl(new BigDecimal("40.00"))
                .totalDistanceKm(new BigDecimal("20.00"))
                .availableSeats(0)
                .rideStatus(RideStatus.COMPLETED)
                .build();
        Ride savedRide = rideRepository.save(completedRide);

        // 1 passenger
        Booking b1 = Booking.builder()
                .rideId(savedRide.getRideId())
                .riderId(201L)
                .bookingTime(LocalDateTime.now())
                .bookingStatus(BookingStatus.CONFIRMED)
                .build();
        bookingRepository.save(b1);

        mockMvc.perform(get("/api/rides/" + savedRide.getRideId() + "/settlement")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rideId", is(savedRide.getRideId().intValue())))
                .andExpect(jsonPath("$.occupantCount", is(2)))
                .andExpect(jsonPath("$.upiDeepLink", containsString("upi://pay?pa=")))
                .andExpect(jsonPath("$.upiDeepLink", containsString("&am=")))
                .andExpect(jsonPath("$.upiDeepLink", containsString("&cu=INR")));
    }

    @Test
    @DisplayName("Swagger UI is accessible without authentication")
    void swaggerUi_AccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi", containsString("3.")))
                .andExpect(jsonPath("$.info.title", is("Campus Commute API")));
    }
}

