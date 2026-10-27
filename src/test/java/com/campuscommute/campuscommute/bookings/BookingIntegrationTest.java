package com.campuscommute.campuscommute.bookings;

import com.campuscommute.campuscommute.auth.JwtUtil;
import com.campuscommute.campuscommute.auth.Role;
import com.campuscommute.campuscommute.rides.GoogleRoutesClient;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class BookingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private GoogleRoutesClient googleRoutesClient;

    private String rider1Token;
    private String rider2Token;
    private String driverToken;
    private Ride testRide;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        rideRepository.deleteAll();

        rider1Token = jwtUtil.generateAccessToken(201L, Role.RIDER);
        rider2Token = jwtUtil.generateAccessToken(202L, Role.RIDER);
        driverToken = jwtUtil.generateAccessToken(101L, Role.DRIVER);

        testRide = Ride.builder()
                .driverId(101L)
                .startLocation("Porur Roundana")
                .destination("EEC Campus")
                .vehicleType(VehicleType.FOUR_WHEELER)
                .mileageKmpl(new BigDecimal("15.00"))
                .totalDistanceKm(new BigDecimal("12.50"))
                .availableSeats(1)
                .rideStatus(RideStatus.SCHEDULED)
                .build();
        testRide = rideRepository.save(testRide);
    }

    @Test
    @DisplayName("AC1: Two concurrent booking requests for a ride with exactly 1 seat: exactly one succeeds, other gets 409 OverbookingException")
    void testConcurrentBookingPessimisticLocking() throws Exception {
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger overbookingCount = new AtomicInteger(0);

        // Rider 1 request
        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult result = mockMvc.perform(post("/api/bookings")
                                .header("Authorization", "Bearer " + rider1Token)
                                .param("rideId", testRide.getRideId().toString()))
                        .andReturn();

                int status = result.getResponse().getStatus();
                if (status == 201) {
                    successCount.incrementAndGet();
                } else if (status == 409) {
                    overbookingCount.incrementAndGet();
                }
            } catch (Exception e) {
                // ignore
            } finally {
                endLatch.countDown();
            }
        });

        // Rider 2 request
        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult result = mockMvc.perform(post("/api/bookings")
                                .header("Authorization", "Bearer " + rider2Token)
                                .param("rideId", testRide.getRideId().toString()))
                        .andReturn();

                int status = result.getResponse().getStatus();
                if (status == 201) {
                    successCount.incrementAndGet();
                } else if (status == 409) {
                    overbookingCount.incrementAndGet();
                }
            } catch (Exception e) {
                // ignore
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(overbookingCount.get()).isEqualTo(1);

        Ride updatedRide = rideRepository.findById(testRide.getRideId()).orElseThrow();
        assertThat(updatedRide.getAvailableSeats()).isEqualTo(0);
        assertThat(updatedRide.getRideStatus()).isEqualTo(RideStatus.FULL);
        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC2: Rider cannot book the same ride twice while their first booking is CONFIRMED")
    void testDuplicateBookingPrevented() throws Exception {
        // First booking succeeds
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + rider1Token)
                        .param("rideId", testRide.getRideId().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingStatus", is("CONFIRMED")));

        // Give the ride another seat to test duplicate check specifically (not seat check)
        testRide = rideRepository.findById(testRide.getRideId()).orElseThrow();
        testRide.setAvailableSeats(1);
        testRide.setRideStatus(RideStatus.SCHEDULED);
        rideRepository.save(testRide);

        // Second booking by same rider on same ride fails with 409 Conflict
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + rider1Token)
                        .param("rideId", testRide.getRideId().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", is("Rider already has a confirmed booking for this ride")));
    }

    @Test
    @DisplayName("AC3a: Cancelling a CONFIRMED booking on a FULL ride restores seat and flips status back to SCHEDULED")
    void testCancelBookingRestoresSeatAndFlipsFullToScheduled() throws Exception {
        // Book the last seat -> status becomes FULL, availableSeats becomes 0
        MvcResult bookResult = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + rider1Token)
                        .param("rideId", testRide.getRideId().toString()))
                .andExpect(status().isCreated())
                .andReturn();

        Ride fullRide = rideRepository.findById(testRide.getRideId()).orElseThrow();
        assertThat(fullRide.getAvailableSeats()).isEqualTo(0);
        assertThat(fullRide.getRideStatus()).isEqualTo(RideStatus.FULL);

        Long bookingId = bookingRepository.findAll().get(0).getBookingId();

        // Cancel the booking
        mockMvc.perform(patch("/api/bookings/{id}/cancel", bookingId)
                        .header("Authorization", "Bearer " + rider1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus", is("CANCELLED")));

        // Verify ride seats and status restored
        Ride restoredRide = rideRepository.findById(testRide.getRideId()).orElseThrow();
        assertThat(restoredRide.getAvailableSeats()).isEqualTo(1);
        assertThat(restoredRide.getRideStatus()).isEqualTo(RideStatus.SCHEDULED);
    }

    @Test
    @DisplayName("AC3b: Cancelling a booking on an ONGOING or COMPLETED ride is rejected with a clear 409 error, not 500")
    void testCancelBookingOnOngoingOrCompletedRideRejected() throws Exception {
        // Book seat
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + rider1Token)
                        .param("rideId", testRide.getRideId().toString()))
                .andExpect(status().isCreated());

        Long bookingId = bookingRepository.findAll().get(0).getBookingId();

        // Advance ride to ONGOING
        testRide = rideRepository.findById(testRide.getRideId()).orElseThrow();
        testRide.setRideStatus(RideStatus.ONGOING);
        rideRepository.save(testRide);

        // Attempt cancel while ONGOING
        mockMvc.perform(patch("/api/bookings/{id}/cancel", bookingId)
                        .header("Authorization", "Bearer " + rider1Token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("Conflict")));

        // Advance ride to COMPLETED
        testRide.setRideStatus(RideStatus.COMPLETED);
        rideRepository.save(testRide);

        // Attempt cancel while COMPLETED
        mockMvc.perform(patch("/api/bookings/{id}/cancel", bookingId)
                        .header("Authorization", "Bearer " + rider1Token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("Conflict")));
    }

    @Test
    @DisplayName("AC4: Cancelling booking owned by another rider is rejected with 403 Forbidden")
    void testCancelBookingOwnedByAnotherRejected() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + rider1Token)
                        .param("rideId", testRide.getRideId().toString()))
                .andExpect(status().isCreated());

        Long bookingId = bookingRepository.findAll().get(0).getBookingId();

        // Rider 2 attempts to cancel Rider 1's booking
        mockMvc.perform(patch("/api/bookings/{id}/cancel", bookingId)
                        .header("Authorization", "Bearer " + rider2Token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC5: Non-rider (driver only) is rejected on POST /api/bookings with 403 Forbidden")
    void testNonRiderCannotBook() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + driverToken)
                        .param("rideId", testRide.getRideId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC6: GET /api/bookings/me returns the authenticated rider's bookings")
    void testGetMyBookings() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + rider1Token)
                        .param("rideId", testRide.getRideId().toString()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/bookings/me")
                        .header("Authorization", "Bearer " + rider1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].riderId", is(201)));
    }

    @Test
    @DisplayName("AC7: Driver can view bookings for their ride, other users cannot")
    void testDriverViewBookingsForRide() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + rider1Token)
                        .param("rideId", testRide.getRideId().toString()))
                .andExpect(status().isCreated());

        // Driver of testRide views bookings
        mockMvc.perform(get("/api/rides/{id}/bookings", testRide.getRideId())
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].riderId", is(201)));

        // Another driver views testRide bookings -> 403
        String otherDriverToken = jwtUtil.generateAccessToken(999L, Role.DRIVER);
        mockMvc.perform(get("/api/rides/{id}/bookings", testRide.getRideId())
                        .header("Authorization", "Bearer " + otherDriverToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC8: Booking with JSON request body is also supported")
    void testBookingWithRequestBody() throws Exception {
        String json = "{\"rideId\": " + testRide.getRideId() + "}";

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + rider1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rideId", is(testRide.getRideId().intValue())))
                .andExpect(jsonPath("$.riderId", is(201)))
                .andExpect(jsonPath("$.bookingStatus", is("CONFIRMED")));
    }
}

