package com.campuscommute.campuscommute.bookings;

import com.campuscommute.campuscommute.auth.JwtUtil;
import com.campuscommute.campuscommute.auth.Role;
import com.campuscommute.campuscommute.auth.StudentDirectory;
import com.campuscommute.campuscommute.auth.StudentDirectoryRepository;
import com.campuscommute.campuscommute.auth.User;
import com.campuscommute.campuscommute.auth.UserRepository;
import com.campuscommute.campuscommute.rides.GoogleRoutesClient;
import com.campuscommute.campuscommute.rides.Ride;
import com.campuscommute.campuscommute.rides.RideRepository;
import com.campuscommute.campuscommute.rides.RideService;
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
import java.time.LocalDateTime;
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
    private RideService rideService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentDirectoryRepository studentDirectoryRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private GoogleRoutesClient googleRoutesClient;

    private String rider1Token;
    private String rider2Token;
    private String driverToken;
    private User driverUser;
    private User riderUser;
    private Ride testRide;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        rideRepository.deleteAll();
        userRepository.deleteAll();

        // Seed student directory records
        if (!studentDirectoryRepository.existsById("310621104001")) {
            studentDirectoryRepository.save(new StudentDirectory("310621104001", "Kavitha R", "Computer Science and Engineering", 3));
        }
        if (!studentDirectoryRepository.existsById("310621104002")) {
            studentDirectoryRepository.save(new StudentDirectory("310621104002", "Arun Kumar S", "Information Technology", 4));
        }

        // Create driver user (ID 101L)
        driverUser = new User("310621104001", "9876543210", "pass123", Role.DRIVER);
        driverUser = userRepository.save(driverUser);

        // Create rider user (ID 201L)
        riderUser = new User("310621104002", "9123456780", "pass123", Role.RIDER);
        riderUser = userRepository.save(riderUser);

        rider1Token = jwtUtil.generateAccessToken(riderUser.getUserId(), Role.RIDER);
        rider2Token = jwtUtil.generateAccessToken(202L, Role.RIDER);
        driverToken = jwtUtil.generateAccessToken(driverUser.getUserId(), Role.DRIVER);

        testRide = Ride.builder()
                .driverId(driverUser.getUserId())
                .startLocation("Porur Roundana")
                .destination(Ride.EEC_NAME)
                .vehicleType(VehicleType.FOUR_WHEELER)
                .vehicleModel("Swift Dzire")
                .vehiclePlate("TN09AB1234")
                .departureTime(LocalDateTime.now().plusHours(2))
                .pickupLat(13.0359)
                .pickupLng(80.1568)
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
    @DisplayName("Phase 3: User cannot book their own ride (blocked with 409 Conflict)")
    void testUserCannotBookOwnRide() throws Exception {
        // Driver attempts to book their own ride using a rider token with driver's userId
        String driverAsRiderToken = jwtUtil.generateAccessToken(driverUser.getUserId(), Role.RIDER);
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + driverAsRiderToken)
                        .param("rideId", testRide.getRideId().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Drivers cannot book a seat on their own ride")));
    }

    @Test
    @DisplayName("Phase 3: Double-cancel of the same booking keeps seat count correct and does not add seat twice")
    void testDoubleCancelKeepsSeatCountCorrect() throws Exception {
        // Book the single seat
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + rider1Token)
                        .param("rideId", testRide.getRideId().toString()))
                .andExpect(status().isCreated());

        Long bookingId = bookingRepository.findAll().get(0).getBookingId();

        // First cancel restores seat count from 0 to 1
        mockMvc.perform(patch("/api/bookings/{id}/cancel", bookingId)
                        .header("Authorization", "Bearer " + rider1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus", is("CANCELLED")));

        Ride rideAfterFirstCancel = rideRepository.findById(testRide.getRideId()).orElseThrow();
        assertThat(rideAfterFirstCancel.getAvailableSeats()).isEqualTo(1);
        assertThat(rideAfterFirstCancel.getRideStatus()).isEqualTo(RideStatus.SCHEDULED);

        // Second cancel on the same booking must NOT add another seat
        mockMvc.perform(patch("/api/bookings/{id}/cancel", bookingId)
                        .header("Authorization", "Bearer " + rider1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus", is("CANCELLED")));

        Ride rideAfterSecondCancel = rideRepository.findById(testRide.getRideId()).orElseThrow();
        assertThat(rideAfterSecondCancel.getAvailableSeats()).isEqualTo(1);
    }

    @Test
    @DisplayName("Phase 3: Driver cancel cascades to confirmed bookings and subsequent booking cancel does not add seat back")
    void testDriverCancelCascadesToConfirmedBookings() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + rider1Token)
                        .param("rideId", testRide.getRideId().toString()))
                .andExpect(status().isCreated());

        Long bookingId = bookingRepository.findAll().get(0).getBookingId();

        // Driver cancels the ride
        rideService.updateRideStatus(testRide.getRideId(), driverUser.getUserId(), RideStatus.CANCELLED);

        // Verify booking cascaded to CANCELLED
        Booking cascadedBooking = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(cascadedBooking.getBookingStatus()).isEqualTo(BookingStatus.CANCELLED);

        // Rider cancels their booking -> seat is NOT added back to a CANCELLED ride
        mockMvc.perform(patch("/api/bookings/{id}/cancel", bookingId)
                        .header("Authorization", "Bearer " + rider1Token))
                .andExpect(status().isOk());

        Ride cancelledRide = rideRepository.findById(testRide.getRideId()).orElseThrow();
        assertThat(cancelledRide.getAvailableSeats()).isEqualTo(0);
        assertThat(cancelledRide.getRideStatus()).isEqualTo(RideStatus.CANCELLED);
    }

    @Test
    @DisplayName("Phase 3: BookingResponse includes ride summary and driver contact for CONFIRMED bookings")
    void testBookingResponseIncludesRideSummaryAndDriverContact() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + rider1Token)
                        .param("rideId", testRide.getRideId().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startLocation", is("Porur Roundana")))
                .andExpect(jsonPath("$.destination", is(Ride.EEC_NAME)))
                .andExpect(jsonPath("$.vehicleModel", is("Swift Dzire")))
                .andExpect(jsonPath("$.vehiclePlate", is("TN09AB1234")))
                .andExpect(jsonPath("$.driverName", is("Kavitha R")))
                .andExpect(jsonPath("$.driverPhone", is("9876543210")));

        // Driver views bookings for this ride: receives riderName, riderDepartment, riderPhone
        mockMvc.perform(get("/api/rides/{id}/bookings", testRide.getRideId())
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].riderName", is("Arun Kumar S")))
                .andExpect(jsonPath("$[0].riderDepartment", is("Information Technology")))
                .andExpect(jsonPath("$[0].riderPhone", is("9123456780")));
    }

    @Test
    @DisplayName("AC3a: Cancelling a CONFIRMED booking on a FULL ride restores seat and flips status back to SCHEDULED")
    void testCancelBookingRestoresSeatAndFlipsFullToScheduled() throws Exception {
        // Book the last seat -> status becomes FULL, availableSeats becomes 0
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + rider1Token)
                        .param("rideId", testRide.getRideId().toString()))
                .andExpect(status().isCreated());

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
                .andExpect(jsonPath("$[0].riderId", is(riderUser.getUserId().intValue())));
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
                .andExpect(jsonPath("$.riderId", is(riderUser.getUserId().intValue())))
                .andExpect(jsonPath("$.bookingStatus", is("CONFIRMED")));
    }
}
