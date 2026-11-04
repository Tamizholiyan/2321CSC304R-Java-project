package com.campuscommute.campuscommute.rides;

import com.campuscommute.campuscommute.auth.JwtUtil;
import com.campuscommute.campuscommute.auth.Role;
import com.campuscommute.campuscommute.auth.User;
import com.campuscommute.campuscommute.auth.UserRepository;
import com.campuscommute.campuscommute.bookings.Booking;
import com.campuscommute.campuscommute.bookings.BookingRepository;
import com.campuscommute.campuscommute.bookings.BookingStatus;
import com.campuscommute.campuscommute.bookings.dto.CreateBookingRequest;
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
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ScheduleConflictIntegrationTest {

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

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private GoogleRoutesClient googleRoutesClient;

    private Long userAId;
    private Long userBId;
    private Long userCId;

    private String tokenA_Driver;
    private String tokenA_Rider;
    private String tokenB_Driver;
    private String tokenB_Rider;
    private String tokenC_Driver;
    private String tokenC_Rider;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        rideRepository.deleteAll();

        // User A (310621104001)
        User userA = userRepository.findByRegisterNumber("310621104001").orElseGet(() ->
                userRepository.save(new User("310621104001", "9876543210", "$2a$10$FrOdk9kaqeHOCf3gaMEM1uhGLWgp8v9lQ5SwhzKBLFIKRw2o2zez6", Role.DRIVER))
        );
        userAId = userA.getUserId();
        tokenA_Driver = jwtUtil.generateAccessToken(userAId, Role.DRIVER);
        tokenA_Rider = jwtUtil.generateAccessToken(userAId, Role.RIDER);

        // User B (310621104002)
        User userB = userRepository.findByRegisterNumber("310621104002").orElseGet(() ->
                userRepository.save(new User("310621104002", "9876543211", "$2a$10$FrOdk9kaqeHOCf3gaMEM1uhGLWgp8v9lQ5SwhzKBLFIKRw2o2zez6", Role.RIDER))
        );
        userBId = userB.getUserId();
        tokenB_Driver = jwtUtil.generateAccessToken(userBId, Role.DRIVER);
        tokenB_Rider = jwtUtil.generateAccessToken(userBId, Role.RIDER);

        // User C (310621104006)
        User userC = userRepository.findByRegisterNumber("310621104006").orElseGet(() ->
                userRepository.save(new User("310621104006", "9876543216", "$2a$10$FrOdk9kaqeHOCf3gaMEM1uhGLWgp8v9lQ5SwhzKBLFIKRw2o2zez6", Role.DRIVER))
        );
        userCId = userC.getUserId();
        tokenC_Driver = jwtUtil.generateAccessToken(userCId, Role.DRIVER);
        tokenC_Rider = jwtUtil.generateAccessToken(userCId, Role.RIDER);

        when(googleRoutesClient.computeDistanceKm(anyString(), anyString(), any(VehicleType.class)))
                .thenReturn(new BigDecimal("15.75"));
        when(googleRoutesClient.computeDistanceKm(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new BigDecimal("15.75"));
    }

    private CreateRideRequest createRequest(String start, LocalDateTime departure) {
        return new CreateRideRequest(
                start, Ride.EEC_NAME, VehicleType.FOUR_WHEELER,
                "Swift", "TN01AA1111", departure,
                13.0359, 80.1568, 13.0315, 80.1804,
                new BigDecimal("15.0"), 3, "Test Ride"
        );
    }

    // =========================================================================
    // 1. DRIVER CONFLICT TESTS
    // =========================================================================

    @Test
    @DisplayName("Driver: same departure time rejected with 409 Conflict")
    void testDriver_SameDepartureTime_Rejected409() throws Exception {
        LocalDateTime departure = LocalDateTime.now().plusHours(3).truncatedTo(ChronoUnit.MINUTES);
        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Porur", departure))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Vadapalani", departure))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Rides must be at least 90 minutes apart")));
    }

    @Test
    @DisplayName("Driver: 89 minutes apart rejected with 409 Conflict")
    void testDriver_89MinutesApart_Rejected409() throws Exception {
        LocalDateTime departure1 = LocalDateTime.now().plusHours(3).truncatedTo(ChronoUnit.MINUTES);
        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Porur", departure1))))
                .andExpect(status().isCreated());

        LocalDateTime departure2 = departure1.plusMinutes(89);
        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Porur", departure2))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Rides must be at least 90 minutes apart")));
    }

    @Test
    @DisplayName("Driver: exactly 90 minutes apart accepted with 201 Created")
    void testDriver_Exactly90MinutesApart_Accepted201() throws Exception {
        LocalDateTime departure1 = LocalDateTime.now().plusHours(3).truncatedTo(ChronoUnit.MINUTES);
        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Porur", departure1))))
                .andExpect(status().isCreated());

        LocalDateTime departure2 = departure1.plusMinutes(90);
        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Porur", departure2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rideId").isNumber());
    }

    // =========================================================================
    // 2. RIDER CONFLICT TESTS
    // =========================================================================

    @Test
    @DisplayName("Rider: books ride at T, then second ride at T+30 -> 409; at T+90 -> OK")
    void testRider_OverlappingBookings_Enforced() throws Exception {
        LocalDateTime t = LocalDateTime.now().plusHours(4).truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime tPlus30 = t.plusMinutes(30);
        LocalDateTime tPlus90 = t.plusMinutes(90);

        // Driver A creates ride at T
        String res1 = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Porur", t))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long ride1Id = objectMapper.readTree(res1).get("rideId").asLong();

        // Driver C creates ride at T+30
        String res2 = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenC_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Tambaram", tPlus30))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long ride2Id = objectMapper.readTree(res2).get("rideId").asLong();

        // Driver A creates ride at T+90 (Driver A's rides are exactly 90 min apart)
        String res3 = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Porur", tPlus90))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long ride3Id = objectMapper.readTree(res3).get("rideId").asLong();

        // Rider B books ride 1 (at T) -> 201 Created
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + tokenB_Rider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(ride1Id))))
                .andExpect(status().isCreated());

        // Rider B attempts to book ride 2 (at T+30) -> 409 Conflict
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + tokenB_Rider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(ride2Id))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("You already have a booking at")))
                .andExpect(jsonPath("$.message", containsString("Rides must be at least 90 minutes apart")));

        // Rider B books ride 3 (at T+90) -> 201 Created
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + tokenB_Rider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(ride3Id))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").isNumber());
    }

    // =========================================================================
    // 3. CROSS-ROLE CONFLICT TESTS
    // =========================================================================

    @Test
    @DisplayName("Cross-role: rider who has booking at T cannot publish ride at T+30; can at T+90")
    void testCrossRole_RiderBookingBlocksDrivingAtTPlus30() throws Exception {
        LocalDateTime t = LocalDateTime.now().plusHours(4).truncatedTo(ChronoUnit.MINUTES);

        // Driver C publishes ride at T
        String res1 = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenC_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Tambaram", t))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long ride1Id = objectMapper.readTree(res1).get("rideId").asLong();

        // User B books ride 1 at T -> 201 Created
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + tokenB_Rider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(ride1Id))))
                .andExpect(status().isCreated());

        // User B switches to driver role and attempts to publish ride at T+30 -> 409 Conflict
        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenB_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Porur", t.plusMinutes(30)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("You already have a booking at")))
                .andExpect(jsonPath("$.message", containsString("Rides must be at least 90 minutes apart")));

        // User B publishes ride at T+90 -> 201 Created
        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenB_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Porur", t.plusMinutes(90)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rideId").isNumber());
    }

    @Test
    @DisplayName("Cross-role: driver who has ride at T cannot book someone else's ride at T+30; can at T+90")
    void testCrossRole_DriverRideBlocksBookingAtTPlus30() throws Exception {
        LocalDateTime t = LocalDateTime.now().plusHours(4).truncatedTo(ChronoUnit.MINUTES);

        // Driver A publishes ride at T
        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Porur", t))))
                .andExpect(status().isCreated());

        // Driver C publishes ride at T+30
        String resC30 = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenC_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Tambaram", t.plusMinutes(30)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long rideC30Id = objectMapper.readTree(resC30).get("rideId").asLong();

        // Driver C publishes ride at T+120 (90 mins after C's T+30 ride)
        String resC120 = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenC_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Tambaram", t.plusMinutes(120)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long rideC120Id = objectMapper.readTree(resC120).get("rideId").asLong();

        // Driver A (acting as rider) attempts to book C's ride at T+30 -> 409 Conflict
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + tokenA_Rider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(rideC30Id))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("You already have a ride you are driving at")))
                .andExpect(jsonPath("$.message", containsString("Rides must be at least 90 minutes apart")));

        // Driver A books C's ride at T+120 (>= 90m from A's ride at T) -> 201 Created
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + tokenA_Rider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(rideC120Id))))
                .andExpect(status().isCreated());
    }

    // =========================================================================
    // 4. SLOTS FREED TESTS
    // =========================================================================

    @Test
    @DisplayName("Slots freed: cancelling booking frees slot for rider")
    void testSlotFreed_ByCancellingBooking() throws Exception {
        LocalDateTime t = LocalDateTime.now().plusHours(4).truncatedTo(ChronoUnit.MINUTES);

        // Driver A creates ride at T
        String r1 = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Porur", t))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long ride1Id = objectMapper.readTree(r1).get("rideId").asLong();

        // Driver C creates ride at T+30
        String r2 = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenC_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Tambaram", t.plusMinutes(30)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long ride2Id = objectMapper.readTree(r2).get("rideId").asLong();

        // Rider B books ride 1
        String b1 = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + tokenB_Rider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(ride1Id))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long bookingId = objectMapper.readTree(b1).get("bookingId").asLong();

        // Cancel booking 1
        mockMvc.perform(patch("/api/bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + tokenB_Rider))
                .andExpect(status().isOk());

        // Now Rider B can book ride 2 at T+30 without conflict
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + tokenB_Rider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(ride2Id))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Slots freed: driver cancelling ride cancels riders' bookings and frees slots for all")
    void testSlotFreed_ByDriverCancellingRide() throws Exception {
        LocalDateTime t = LocalDateTime.now().plusHours(4).truncatedTo(ChronoUnit.MINUTES);

        // Driver A creates ride at T
        String r1 = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Porur", t))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long ride1Id = objectMapper.readTree(r1).get("rideId").asLong();

        // Rider B books ride 1
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + tokenB_Rider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(ride1Id))))
                .andExpect(status().isCreated());

        // Driver A cancels ride 1 -> also cancels rider B's booking
        mockMvc.perform(patch("/api/rides/" + ride1Id + "/status")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRideStatusRequest(RideStatus.CANCELLED))))
                .andExpect(status().isOk());

        // Driver A can now create a new ride at the same time T
        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Vadapalani", t))))
                .andExpect(status().isCreated());

        // Driver C creates a ride at T; Rider B can now book it
        String rC = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenC_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Tambaram", t))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long rideCId = objectMapper.readTree(rC).get("rideId").asLong();

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + tokenB_Rider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(rideCId))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Slots freed: COMPLETED ride frees slot for driver")
    void testSlotFreed_ByCompletedRide() throws Exception {
        LocalDateTime t = LocalDateTime.now().plusHours(4).truncatedTo(ChronoUnit.MINUTES);

        // Driver A creates ride at T
        String r1 = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Porur", t))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long ride1Id = objectMapper.readTree(r1).get("rideId").asLong();

        // Start and Complete ride
        mockMvc.perform(patch("/api/rides/" + ride1Id + "/status")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRideStatusRequest(RideStatus.ONGOING))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/rides/" + ride1Id + "/status")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRideStatusRequest(RideStatus.COMPLETED))))
                .andExpect(status().isOk());

        // Driver A can now create another ride at T
        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Porur", t))))
                .andExpect(status().isCreated());
    }

    // =========================================================================
    // 5. TWO DIFFERENT USERS NEVER CONFLICT
    // =========================================================================

    @Test
    @DisplayName("Two different drivers at the exact same departure time never conflict")
    void testTwoDifferentDrivers_SameTime_BothSucceed() throws Exception {
        LocalDateTime departure = LocalDateTime.now().plusHours(3).truncatedTo(ChronoUnit.MINUTES);
        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Porur", departure))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenC_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Tambaram", departure))))
                .andExpect(status().isCreated());
    }

    // =========================================================================
    // 6. CONCURRENCY TESTS (two threads, assert exactly one wins)
    // =========================================================================

    @Test
    @DisplayName("Concurrency: same driver publishes two rides simultaneously -> exactly one wins")
    void testConcurrency_SameDriverTwoRidesSimultaneous() throws Exception {
        LocalDateTime departure = LocalDateTime.now().plusHours(5).truncatedTo(ChronoUnit.MINUTES);
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    CreateRideRequest req = createRequest(index == 0 ? "Porur" : "Vadapalani", departure);
                    int statusCode = mockMvc.perform(post("/api/rides")
                                    .header("Authorization", "Bearer " + tokenA_Driver)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                            .andReturn().getResponse().getStatus();
                    if (statusCode == 201) successCount.incrementAndGet();
                    else if (statusCode == 409) conflictCount.incrementAndGet();
                } catch (Exception e) {
                    // ignore
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Concurrency: same rider books two overlapping rides simultaneously -> exactly one wins")
    void testConcurrency_SameRiderTwoOverlappingBookingsSimultaneous() throws Exception {
        LocalDateTime t = LocalDateTime.now().plusHours(5).truncatedTo(ChronoUnit.MINUTES);

        // Driver A creates ride at T
        String rA = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Porur", t))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long rideAId = objectMapper.readTree(rA).get("rideId").asLong();

        // Driver C creates ride at T+30
        String rC = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenC_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Tambaram", t.plusMinutes(30)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long rideCId = objectMapper.readTree(rC).get("rideId").asLong();

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.await();
                int sc = mockMvc.perform(post("/api/bookings")
                                .header("Authorization", "Bearer " + tokenB_Rider)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new CreateBookingRequest(rideAId))))
                        .andReturn().getResponse().getStatus();
                if (sc == 201) successCount.incrementAndGet();
                else if (sc == 409) conflictCount.incrementAndGet();
            } catch (Exception e) {
                // ignore
            } finally {
                endLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                int sc = mockMvc.perform(post("/api/bookings")
                                .header("Authorization", "Bearer " + tokenB_Rider)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new CreateBookingRequest(rideCId))))
                        .andReturn().getResponse().getStatus();
                if (sc == 201) successCount.incrementAndGet();
                else if (sc == 409) conflictCount.incrementAndGet();
            } catch (Exception e) {
                // ignore
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Concurrency: same user publishes ride and books ride that overlap -> exactly one wins")
    void testConcurrency_SameUserPublishesAndBooksOverlappingSimultaneous() throws Exception {
        LocalDateTime t = LocalDateTime.now().plusHours(5).truncatedTo(ChronoUnit.MINUTES);

        // Driver C creates ride at T
        String rC = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenC_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Tambaram", t))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long rideCId = objectMapper.readTree(rC).get("rideId").asLong();

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        // User A tries to publish a ride at T+30 (as driver)
        executor.submit(() -> {
            try {
                startLatch.await();
                int sc = mockMvc.perform(post("/api/rides")
                                .header("Authorization", "Bearer " + tokenA_Driver)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest("Porur", t.plusMinutes(30)))))
                        .andReturn().getResponse().getStatus();
                if (sc == 201) successCount.incrementAndGet();
                else if (sc == 409) conflictCount.incrementAndGet();
            } catch (Exception e) {
                // ignore
            } finally {
                endLatch.countDown();
            }
        });

        // User A tries to book C's ride at T (as rider)
        executor.submit(() -> {
            try {
                startLatch.await();
                int sc = mockMvc.perform(post("/api/bookings")
                                .header("Authorization", "Bearer " + tokenA_Rider)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new CreateBookingRequest(rideCId))))
                        .andReturn().getResponse().getStatus();
                if (sc == 201) successCount.incrementAndGet();
                else if (sc == 409) conflictCount.incrementAndGet();
            } catch (Exception e) {
                // ignore
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);
    }

    // =========================================================================
    // 7. SEARCH CONFLICT WARNINGS
    // =========================================================================

    @Test
    @DisplayName("Search: caller with commitment at T sees conflictMessage on rides within [T-89m..T+89m], and null on rides >= 90m away")
    void testSearch_ConflictMessagePopulatedCorrectly() throws Exception {
        LocalDateTime t = LocalDateTime.now().plusHours(6).truncatedTo(ChronoUnit.MINUTES);

        // Rider B has a booking on Driver A's ride at T
        String rA = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Porur", t))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long rideAId = objectMapper.readTree(rA).get("rideId").asLong();

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + tokenB_Rider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(rideAId))))
                .andExpect(status().isCreated());

        // Driver C creates a ride at T+30 (clashes with Rider B's booking)
        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenC_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Tambaram", t.plusMinutes(30)))))
                .andExpect(status().isCreated());

        // Driver A creates a ride at T+90 (does NOT clash for Driver A, and does NOT clash for Rider B)
        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Porur", t.plusMinutes(90)))))
                .andExpect(status().isCreated());

        // Rider B searches rides
        String searchJson = mockMvc.perform(get("/api/rides")
                        .header("Authorization", "Bearer " + tokenB_Rider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3))) // rideA (driver A), rideC30 (driver C), rideA90 (driver A)
                .andReturn().getResponse().getContentAsString();

        // Verify that the T+30 ride has conflictMessage and the T+90 ride has null conflictMessage
        var ridesArray = objectMapper.readTree(searchJson);
        for (var rideNode : ridesArray) {
            String depStr = rideNode.get("departureTime").asText();
            if (depStr.startsWith(t.plusMinutes(30).truncatedTo(ChronoUnit.MINUTES).toString())) {
                assertThat(rideNode.get("conflictMessage").asText())
                        .contains("You already have a booking at")
                        .contains("Rides must be at least 90 minutes apart");
            } else if (depStr.startsWith(t.plusMinutes(90).truncatedTo(ChronoUnit.MINUTES).toString())) {
                assertThat(rideNode.get("conflictMessage").isNull()).isTrue();
            }
        }
    }

    // =========================================================================
    // 8. STATUS TRANSITIONS
    // =========================================================================

    @Test
    @DisplayName("Status transitions: SCHEDULED -> ONGOING -> COMPLETED, SCHEDULED -> CANCELLED succeed; illegal transitions fail with 409; non-driver fails with 403")
    void testStatusTransitions_Enforced() throws Exception {
        LocalDateTime t = LocalDateTime.now().plusHours(3).truncatedTo(ChronoUnit.MINUTES);

        // Driver A creates ride
        String r = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Porur", t))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long rideId = objectMapper.readTree(r).get("rideId").asLong();

        // Non-driver (User B) attempts to update status -> 403 Forbidden
        mockMvc.perform(patch("/api/rides/" + rideId + "/status")
                        .header("Authorization", "Bearer " + tokenB_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRideStatusRequest(RideStatus.ONGOING))))
                .andExpect(status().isForbidden());

        // Driver A transitions SCHEDULED -> ONGOING -> 200 OK
        mockMvc.perform(patch("/api/rides/" + rideId + "/status")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRideStatusRequest(RideStatus.ONGOING))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rideStatus", is("ONGOING")));

        // Driver A attempts ONGOING -> CANCELLED -> 409 Conflict
        mockMvc.perform(patch("/api/rides/" + rideId + "/status")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRideStatusRequest(RideStatus.CANCELLED))))
                .andExpect(status().isConflict());

        // Driver A transitions ONGOING -> COMPLETED -> 200 OK
        mockMvc.perform(patch("/api/rides/" + rideId + "/status")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRideStatusRequest(RideStatus.COMPLETED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rideStatus", is("COMPLETED")));

        // Driver A attempts COMPLETED -> ONGOING -> 409 Conflict
        mockMvc.perform(patch("/api/rides/" + rideId + "/status")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRideStatusRequest(RideStatus.ONGOING))))
                .andExpect(status().isConflict());

        // Test SCHEDULED -> CANCELLED
        String r2 = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Porur", t.plusHours(5)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long ride2Id = objectMapper.readTree(r2).get("rideId").asLong();

        mockMvc.perform(patch("/api/rides/" + ride2Id + "/status")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRideStatusRequest(RideStatus.CANCELLED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rideStatus", is("CANCELLED")));

        // CANCELLED -> ONGOING -> 409 Conflict
        mockMvc.perform(patch("/api/rides/" + ride2Id + "/status")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRideStatusRequest(RideStatus.ONGOING))))
                .andExpect(status().isConflict());
    }

    // =========================================================================
    // 9. PRE-EXISTING OVERLAPPING ROWS UNTOUCHED
    // =========================================================================

    @Test
    @DisplayName("Pre-existing overlapping rows in DB are untouched")
    void testPreExistingOverlappingRowsInDb_AreUntouched() throws Exception {
        LocalDateTime t = LocalDateTime.now().plusHours(4).truncatedTo(ChronoUnit.MINUTES);

        // Directly insert two overlapping rides into DB for Driver A
        Ride ride1 = Ride.builder()
                .driverId(userAId)
                .startLocation("Porur")
                .destination(Ride.EEC_NAME)
                .vehicleType(VehicleType.FOUR_WHEELER)
                .vehicleModel("Swift")
                .vehiclePlate("TN01AA1111")
                .departureTime(t)
                .pickupLat(13.0359)
                .pickupLng(80.1568)
                .dropLat(13.0315)
                .dropLng(80.1804)
                .notes("Legacy 1")
                .mileageKmpl(new BigDecimal("15.0"))
                .totalDistanceKm(new BigDecimal("10.0"))
                .availableSeats(3)
                .rideStatus(RideStatus.SCHEDULED)
                .build();
        rideRepository.save(ride1);

        Ride ride2 = Ride.builder()
                .driverId(userAId)
                .startLocation("Vadapalani")
                .destination(Ride.EEC_NAME)
                .vehicleType(VehicleType.FOUR_WHEELER)
                .vehicleModel("Swift")
                .vehiclePlate("TN01AA1111")
                .departureTime(t.plusMinutes(10))
                .pickupLat(13.0500)
                .pickupLng(80.2120)
                .dropLat(13.0315)
                .dropLng(80.1804)
                .notes("Legacy 2")
                .mileageKmpl(new BigDecimal("15.0"))
                .totalDistanceKm(new BigDecimal("12.0"))
                .availableSeats(3)
                .rideStatus(RideStatus.SCHEDULED)
                .build();
        rideRepository.save(ride2);

        // Verify both still exist intact in DB
        assertThat(rideRepository.findAll()).hasSize(2);

        // When Driver A tries to create a third ride overlapping either, it rejects with 409
        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + tokenA_Driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Porur", t.plusMinutes(20)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));

        // Both original rows remain in the DB
        assertThat(rideRepository.findAll()).hasSize(2);
    }
}
