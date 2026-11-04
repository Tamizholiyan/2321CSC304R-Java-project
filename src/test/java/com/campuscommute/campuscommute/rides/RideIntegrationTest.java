package com.campuscommute.campuscommute.rides;

import com.campuscommute.campuscommute.auth.JwtUtil;
import com.campuscommute.campuscommute.auth.Role;
import com.campuscommute.campuscommute.auth.User;
import com.campuscommute.campuscommute.auth.UserRepository;
import com.campuscommute.campuscommute.bookings.Booking;
import com.campuscommute.campuscommute.bookings.BookingRepository;
import com.campuscommute.campuscommute.bookings.BookingStatus;
import com.campuscommute.campuscommute.pricing.FuelRateProvider;
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
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private FuelRateProvider fuelRateProvider;

    @MockBean
    private GoogleRoutesClient googleRoutesClient;

    private Long driverId;
    private Long otherDriverId;
    private Long riderId;

    private String driverToken;
    private String otherDriverToken;
    private String riderToken;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        rideRepository.deleteAll();

        User driver = userRepository.findByRegisterNumber("310621104001").orElseGet(() ->
                userRepository.save(new User("310621104001", "9876543210", "$2a$10$FrOdk9kaqeHOCf3gaMEM1uhGLWgp8v9lQ5SwhzKBLFIKRw2o2zez6", Role.DRIVER))
        );
        driver.setRole(Role.DRIVER);
        driver = userRepository.save(driver);
        driverId = driver.getUserId();
        driverToken = jwtUtil.generateAccessToken(driverId, Role.DRIVER);

        User otherDriver = userRepository.findByRegisterNumber("310621104006").orElseGet(() ->
                userRepository.save(new User("310621104006", "9876543216", "$2a$10$FrOdk9kaqeHOCf3gaMEM1uhGLWgp8v9lQ5SwhzKBLFIKRw2o2zez6", Role.DRIVER))
        );
        otherDriver.setRole(Role.DRIVER);
        otherDriver = userRepository.save(otherDriver);
        otherDriverId = otherDriver.getUserId();
        otherDriverToken = jwtUtil.generateAccessToken(otherDriverId, Role.DRIVER);

        User rider = userRepository.findByRegisterNumber("310621104002").orElseGet(() ->
                userRepository.save(new User("310621104002", "9876543211", "$2a$10$FrOdk9kaqeHOCf3gaMEM1uhGLWgp8v9lQ5SwhzKBLFIKRw2o2zez6", Role.RIDER))
        );
        rider.setRole(Role.RIDER);
        rider = userRepository.save(rider);
        riderId = rider.getUserId();
        riderToken = jwtUtil.generateAccessToken(riderId, Role.RIDER);

        when(googleRoutesClient.computeDistanceKm(anyString(), anyString(), any(VehicleType.class)))
                .thenReturn(new BigDecimal("15.75"));
        when(googleRoutesClient.computeDistanceKm(any(), any(), any(), any(), any(), any(), any()))
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
    @DisplayName("AC2 & AC5: Creating a ride sets status SCHEDULED, seats requested, EEC destination, and server distance")
    void testCreateRideSuccess() throws Exception {
        CreateRideRequest request = new CreateRideRequest(
                "Vadapalani Metro",
                Ride.EEC_NAME,
                VehicleType.TWO_WHEELER,
                "Hero Splendor",
                "TN09AZ9999",
                LocalDateTime.now().plusHours(3),
                13.0500,
                80.2120,
                new BigDecimal("45.00"),
                1,
                "Near pillar 42"
        );

        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rideId").isNumber())
                .andExpect(jsonPath("$.driverId", is(driverId.intValue())))
                .andExpect(jsonPath("$.startLocation", is("Vadapalani Metro")))
                .andExpect(jsonPath("$.destination", is(Ride.EEC_NAME)))
                .andExpect(jsonPath("$.vehicleType", is("TWO_WHEELER")))
                .andExpect(jsonPath("$.vehicleModel", is("Hero Splendor")))
                .andExpect(jsonPath("$.vehiclePlate", is("TN09AZ9999")))
                .andExpect(jsonPath("$.notes", is("Near pillar 42")))
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
                .driverId(driverId)
                .startLocation("Easwari")
                .destination(Ride.EEC_NAME)
                .departureTime(LocalDateTime.now().plusHours(2))
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
                .andExpect(jsonPath("$.message").value(containsString("Illegal status transition")));

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
                .driverId(driverId)
                .startLocation("EEC")
                .destination(Ride.EEC_NAME)
                .departureTime(LocalDateTime.now().plusHours(1))
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
                .andExpect(jsonPath("$.message").value(containsString("Cannot delete ride while it is ONGOING")));

        assertThat(rideRepository.existsById(ongoingRide.getRideId())).isTrue();
    }

    @Test
    @DisplayName("DELETE /api/rides/{id} on a SCHEDULED ride without bookings returns 204 No Content")
    void testDeleteScheduledRideReturns204() throws Exception {
        Ride scheduledRide = Ride.builder()
                .driverId(driverId)
                .startLocation("EEC")
                .destination(Ride.EEC_NAME)
                .departureTime(LocalDateTime.now().plusHours(2))
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
    @DisplayName("Phase 2: Ownership - modifying status or deleting a ride as a non-owner returns 403 Forbidden")
    void testOwnershipSecurityEnforced() throws Exception {
        Ride ride = Ride.builder()
                .driverId(driverId)
                .startLocation("Porur")
                .destination(Ride.EEC_NAME)
                .departureTime(LocalDateTime.now().plusHours(2))
                .vehicleType(VehicleType.FOUR_WHEELER)
                .mileageKmpl(new BigDecimal("15.00"))
                .totalDistanceKm(new BigDecimal("10.00"))
                .availableSeats(3)
                .rideStatus(RideStatus.SCHEDULED)
                .build();
        ride = rideRepository.save(ride);

        // Another driver tries to change status
        mockMvc.perform(patch("/api/rides/" + ride.getRideId() + "/status")
                        .header("Authorization", "Bearer " + otherDriverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRideStatusRequest(RideStatus.CANCELLED))))
                .andExpect(status().isForbidden());

        // Another driver tries to delete
        mockMvc.perform(delete("/api/rides/" + ride.getRideId())
                        .header("Authorization", "Bearer " + otherDriverToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Phase 2: Delete allowed only when ride has no CONFIRMED bookings, else 409")
    void testDeleteWithConfirmedBookingsReturns409() throws Exception {
        Ride ride = Ride.builder()
                .driverId(driverId)
                .startLocation("Porur")
                .destination(Ride.EEC_NAME)
                .departureTime(LocalDateTime.now().plusHours(2))
                .vehicleType(VehicleType.FOUR_WHEELER)
                .mileageKmpl(new BigDecimal("15.00"))
                .totalDistanceKm(new BigDecimal("10.00"))
                .availableSeats(3)
                .rideStatus(RideStatus.SCHEDULED)
                .build();
        ride = rideRepository.save(ride);

        // Add confirmed booking
        Booking booking = Booking.builder()
                .rideId(ride.getRideId())
                .riderId(riderId)
                .bookingStatus(BookingStatus.CONFIRMED)
                .bookingTime(LocalDateTime.now())
                .build();
        bookingRepository.save(booking);

        mockMvc.perform(delete("/api/rides/" + ride.getRideId())
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("cancel the ride instead")));
    }

    @Test
    @DisplayName("Phase 2: Search returns only SCHEDULED rides with future departure and excludes caller's own rides")
    void testSearchExcludesOwnRidesAndPastDepartures() throws Exception {
        // Driver's ride
        Ride ownRide = Ride.builder()
                .driverId(driverId)
                .startLocation("Porur")
                .destination(Ride.EEC_NAME)
                .departureTime(LocalDateTime.now().plusHours(2))
                .vehicleType(VehicleType.FOUR_WHEELER)
                .mileageKmpl(new BigDecimal("15.00"))
                .totalDistanceKm(new BigDecimal("10.00"))
                .availableSeats(3)
                .rideStatus(RideStatus.SCHEDULED)
                .build();

        // Other driver's open ride
        Ride otherRide = Ride.builder()
                .driverId(otherDriverId)
                .startLocation("Guindy")
                .destination(Ride.EEC_NAME)
                .departureTime(LocalDateTime.now().plusHours(3))
                .vehicleType(VehicleType.TWO_WHEELER)
                .mileageKmpl(new BigDecimal("45.00"))
                .totalDistanceKm(new BigDecimal("8.00"))
                .availableSeats(1)
                .rideStatus(RideStatus.SCHEDULED)
                .build();

        // Other driver's completed ride (should not be in search)
        Ride completedRide = Ride.builder()
                .driverId(otherDriverId)
                .startLocation("Vadapalani")
                .destination(Ride.EEC_NAME)
                .departureTime(LocalDateTime.now().plusHours(1))
                .vehicleType(VehicleType.FOUR_WHEELER)
                .mileageKmpl(new BigDecimal("14.00"))
                .totalDistanceKm(new BigDecimal("7.00"))
                .availableSeats(2)
                .rideStatus(RideStatus.COMPLETED)
                .build();

        rideRepository.save(ownRide);
        rideRepository.save(otherRide);
        rideRepository.save(completedRide);

        // When Driver searches, own ride is excluded -> only otherRide returned
        mockMvc.perform(get("/api/rides")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].driverId", is(otherDriverId.intValue())));

        // When Rider searches, both open rides returned
        mockMvc.perform(get("/api/rides")
                        .header("Authorization", "Bearer " + riderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Driver checks /api/rides/mine -> returns own ride
        mockMvc.perform(get("/api/rides/mine")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].driverId", is(driverId.intValue())));
    }

    @Test
    @DisplayName("Phase 2: Driver cancelling ride cascades CANCELLED status to all confirmed bookings")
    void testDriverCancelCascadesToConfirmedBookings() throws Exception {
        Ride ride = Ride.builder()
                .driverId(driverId)
                .startLocation("Porur")
                .destination(Ride.EEC_NAME)
                .departureTime(LocalDateTime.now().plusHours(2))
                .vehicleType(VehicleType.FOUR_WHEELER)
                .mileageKmpl(new BigDecimal("15.00"))
                .totalDistanceKm(new BigDecimal("10.00"))
                .availableSeats(2)
                .rideStatus(RideStatus.SCHEDULED)
                .build();
        ride = rideRepository.save(ride);

        Booking booking = Booking.builder()
                .rideId(ride.getRideId())
                .riderId(riderId)
                .bookingStatus(BookingStatus.CONFIRMED)
                .bookingTime(LocalDateTime.now())
                .build();
        booking = bookingRepository.save(booking);

        // Driver cancels ride
        mockMvc.perform(patch("/api/rides/" + ride.getRideId() + "/status")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRideStatusRequest(RideStatus.CANCELLED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rideStatus", is("CANCELLED")));

        // Check booking was cascaded to CANCELLED
        Booking updatedBooking = bookingRepository.findById(booking.getBookingId()).orElseThrow();
        assertThat(updatedBooking.getBookingStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("Phase 2: Completing a ride snapshots the fuel rate per litre")
    void testCompletingRideSnapshotsFuelRate() throws Exception {
        Ride ride = Ride.builder()
                .driverId(driverId)
                .startLocation("Porur")
                .destination(Ride.EEC_NAME)
                .departureTime(LocalDateTime.now().plusHours(2))
                .vehicleType(VehicleType.FOUR_WHEELER)
                .mileageKmpl(new BigDecimal("15.00"))
                .totalDistanceKm(new BigDecimal("10.00"))
                .availableSeats(3)
                .rideStatus(RideStatus.ONGOING)
                .build();
        ride = rideRepository.save(ride);

        mockMvc.perform(patch("/api/rides/" + ride.getRideId() + "/status")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRideStatusRequest(RideStatus.COMPLETED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rideStatus", is("COMPLETED")));

        Ride completed = rideRepository.findById(ride.getRideId()).orElseThrow();
        assertThat(completed.getFuelRatePerLitre()).isNotNull();
        assertThat(completed.getFuelRatePerLitre()).isEqualByComparingTo(fuelRateProvider.getCurrentRatePerLitre());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    @DisplayName("Module 3 dependency check: findByIdForUpdate locks and retrieves ride")
    void testFindByIdForUpdate() {
        Ride ride = Ride.builder()
                .driverId(driverId)
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

    // ==========================================
    // PHASE 8: RIDE SCHEDULING & PROXIMITY TESTS
    // ==========================================

    @Test
    @DisplayName("Phase 8: Same departure time for the same driver rejected with 409 Conflict")
    void testCreateRide_SameDepartureTime_RejectedWith409() throws Exception {
        LocalDateTime departure = LocalDateTime.now().plusHours(2).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        CreateRideRequest request1 = new CreateRideRequest(
                "Porur", Ride.EEC_NAME, VehicleType.FOUR_WHEELER,
                "Swift", "TN01AA1111", departure,
                13.0359, 80.1568, 13.0315, 80.1804,
                new BigDecimal("15.0"), 3, "Ride 1"
        );
        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        CreateRideRequest request2 = new CreateRideRequest(
                "Vadapalani", Ride.EEC_NAME, VehicleType.FOUR_WHEELER,
                "Swift", "TN01AA1111", departure,
                13.0500, 80.2120, 13.0315, 80.1804,
                new BigDecimal("15.0"), 3, "Ride 2"
        );
        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("Rides must be at least 90 minutes apart")));
    }

    @Test
    @DisplayName("Phase 8: Departure time 30 minutes apart rejected with 409 Conflict")
    void testCreateRide_30MinutesApart_RejectedWith409() throws Exception {
        LocalDateTime departure1 = LocalDateTime.now().plusHours(2).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        CreateRideRequest request1 = new CreateRideRequest(
                "Porur", Ride.EEC_NAME, VehicleType.FOUR_WHEELER,
                "Swift", "TN01AA1111", departure1,
                13.0359, 80.1568, 13.0315, 80.1804,
                new BigDecimal("15.0"), 3, "Ride 1"
        );
        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        LocalDateTime departure2 = departure1.plusMinutes(30);
        CreateRideRequest request2 = new CreateRideRequest(
                "Porur", Ride.EEC_NAME, VehicleType.FOUR_WHEELER,
                "Swift", "TN01AA1111", departure2,
                13.0359, 80.1568, 13.0315, 80.1804,
                new BigDecimal("15.0"), 3, "Ride 2"
        );
        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("Rides must be at least 90 minutes apart")));
    }

    @Test
    @DisplayName("Phase 8: Departure time exactly 90 minutes apart accepted with 201 Created")
    void testCreateRide_Exactly90MinutesApart_AcceptedWith201() throws Exception {
        LocalDateTime departure1 = LocalDateTime.now().plusHours(2).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        CreateRideRequest request1 = new CreateRideRequest(
                "Porur", Ride.EEC_NAME, VehicleType.FOUR_WHEELER,
                "Swift", "TN01AA1111", departure1,
                13.0359, 80.1568, 13.0315, 80.1804,
                new BigDecimal("15.0"), 3, "Ride 1"
        );
        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        LocalDateTime departure2 = departure1.plusMinutes(90);
        CreateRideRequest request2 = new CreateRideRequest(
                "Porur", Ride.EEC_NAME, VehicleType.FOUR_WHEELER,
                "Swift", "TN01AA1111", departure2,
                13.0359, 80.1568, 13.0315, 80.1804,
                new BigDecimal("15.0"), 3, "Ride 2"
        );
        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rideId").isNumber());
    }

    @Test
    @DisplayName("Phase 8: Cancelled or completed ride frees the departure time slot")
    void testCreateRide_CancelledOrCompletedRide_FreesSlot() throws Exception {
        LocalDateTime departure = LocalDateTime.now().plusHours(2).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        CreateRideRequest request = new CreateRideRequest(
                "Porur", Ride.EEC_NAME, VehicleType.FOUR_WHEELER,
                "Swift", "TN01AA1111", departure,
                13.0359, 80.1568, 13.0315, 80.1804,
                new BigDecimal("15.0"), 3, "Ride 1"
        );
        String res = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long rideId = objectMapper.readTree(res).get("rideId").asLong();

        // Cancel the ride
        mockMvc.perform(patch("/api/rides/" + rideId + "/status")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRideStatusRequest(RideStatus.CANCELLED))))
                .andExpect(status().isOk());

        // Creating ride at same departure time now succeeds
        String res2 = mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long rideId2 = objectMapper.readTree(res2).get("rideId").asLong();

        // Start and Complete ride 2
        mockMvc.perform(patch("/api/rides/" + rideId2 + "/status")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRideStatusRequest(RideStatus.ONGOING))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/rides/" + rideId2 + "/status")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRideStatusRequest(RideStatus.COMPLETED))))
                .andExpect(status().isOk());

        // Completed ride also frees the slot
        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Phase 8: Two different drivers creating rides at the same time are both accepted")
    void testCreateRide_TwoDifferentDriversSameTime_BothAccepted() throws Exception {
        LocalDateTime departure = LocalDateTime.now().plusHours(2).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        CreateRideRequest request1 = new CreateRideRequest(
                "Porur", Ride.EEC_NAME, VehicleType.FOUR_WHEELER,
                "Swift", "TN01AA1111", departure,
                13.0359, 80.1568, 13.0315, 80.1804,
                new BigDecimal("15.0"), 3, "Driver 1 Ride"
        );
        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        CreateRideRequest request2 = new CreateRideRequest(
                "Vadapalani", Ride.EEC_NAME, VehicleType.TWO_WHEELER,
                "Activa", "TN01BB2222", departure,
                13.0500, 80.2120, 13.0315, 80.1804,
                new BigDecimal("45.0"), 1, "Driver 2 Ride"
        );
        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + otherDriverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Phase 8: Concurrent ride creation for same driver gives exactly one winner")
    void testCreateRide_TwoConcurrentCreates_ExactlyOneWinner() throws Exception {
        LocalDateTime departure = LocalDateTime.now().plusHours(2).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        CreateRideRequest request = new CreateRideRequest(
                "Porur", Ride.EEC_NAME, VehicleType.FOUR_WHEELER,
                "Swift", "TN01AA1111", departure,
                13.0359, 80.1568, 13.0315, 80.1804,
                new BigDecimal("15.0"), 3, "Concurrent Test"
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Callable<Void> task = () -> {
            startLatch.await();
            try {
                int respStatus = mockMvc.perform(post("/api/rides")
                                .header("Authorization", "Bearer " + driverToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andReturn().getResponse().getStatus();
                if (respStatus == 201) successCount.incrementAndGet();
                else if (respStatus == 409) conflictCount.incrementAndGet();
            } catch (Exception ignored) {}
            return null;
        };

        Future<Void> f1 = executor.submit(task);
        Future<Void> f2 = executor.submit(task);
        startLatch.countDown();
        f1.get();
        f2.get();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Phase 8: Ride with neither end near EEC rejected with 400 Bad Request")
    void testCreateRide_NeitherEndNearEec_RejectedWith400() throws Exception {
        // Tambaram (12.9249, 80.1000) to Guindy (13.0067, 80.2026), both > 500m from EEC (13.0315, 80.1804)
        CreateRideRequest request = new CreateRideRequest(
                "Tambaram",
                "Guindy",
                VehicleType.FOUR_WHEELER,
                "Swift",
                "TN01AA1111",
                LocalDateTime.now().plusHours(2),
                12.9249,
                80.1000,
                13.0067,
                80.2026,
                new BigDecimal("15.0"),
                3,
                "Non-EEC ride"
        );

        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", is("One end of the ride must be Easwari Engineering College")));
    }

    @Test
    @DisplayName("Phase 8: Driver A creates a ride -> rider B sees it in GET /api/rides and driver A does not")
    void testSearchRides_DriverDoesNotSeeOwnRide_RiderSeesRide() throws Exception {
        CreateRideRequest request = new CreateRideRequest(
                "Porur", Ride.EEC_NAME, VehicleType.FOUR_WHEELER,
                "Swift", "TN01AA1111", LocalDateTime.now().plusHours(2),
                13.0359, 80.1568, 13.0315, 80.1804,
                new BigDecimal("15.0"), 3, "Note"
        );
        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Driver A searches -> own ride is excluded
        mockMvc.perform(get("/api/rides")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Rider B searches -> sees Driver A's ride
        mockMvc.perform(get("/api/rides")
                        .header("Authorization", "Bearer " + riderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].driverId", is(driverId.intValue())));
    }

    @Test
    @DisplayName("Phase 8: Proximity filter returns near rides with pickupDistanceKm and hides far ones")
    void testSearchRides_ProximityFilter_ReturnsNearRidesAndHidesFarOnes() throws Exception {
        // Driver creates ride with pickup at Ramapuram (13.0320, 80.1810) and drop at EEC (13.0315, 80.1804)
        CreateRideRequest request = new CreateRideRequest(
                "Ramapuram", Ride.EEC_NAME, VehicleType.FOUR_WHEELER,
                "Swift", "TN01AA1111", LocalDateTime.now().plusHours(2),
                13.0320, 80.1810, 13.0315, 80.1804,
                new BigDecimal("15.0"), 3, "Near EEC"
        );
        mockMvc.perform(post("/api/rides")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Rider B searches nearby (within 500m of Ramapuram & EEC)
        mockMvc.perform(get("/api/rides")
                        .header("Authorization", "Bearer " + riderToken)
                        .param("pickupLat", "13.0322")
                        .param("pickupLng", "80.1812")
                        .param("dropLat", "13.0316")
                        .param("dropLng", "80.1805"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].pickupDistanceKm").isNumber());

        // Rider B searches far away (pickup in Tambaram 12.9249, 80.1000 - ~14km away)
        mockMvc.perform(get("/api/rides")
                        .header("Authorization", "Bearer " + riderToken)
                        .param("pickupLat", "12.9249")
                        .param("pickupLng", "80.1000")
                        .param("dropLat", "13.0315")
                        .param("dropLng", "80.1804"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }
}
