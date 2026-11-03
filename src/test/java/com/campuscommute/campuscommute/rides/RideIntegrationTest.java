package com.campuscommute.campuscommute.rides;

import com.campuscommute.campuscommute.auth.JwtUtil;
import com.campuscommute.campuscommute.auth.Role;
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
    private BookingRepository bookingRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private FuelRateProvider fuelRateProvider;

    @MockBean
    private GoogleRoutesClient googleRoutesClient;

    private String driverToken;
    private String otherDriverToken;
    private String riderToken;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        rideRepository.deleteAll();

        driverToken = jwtUtil.generateAccessToken(100L, Role.DRIVER);
        otherDriverToken = jwtUtil.generateAccessToken(101L, Role.DRIVER);
        riderToken = jwtUtil.generateAccessToken(200L, Role.RIDER);

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
                .andExpect(jsonPath("$.driverId", is(100)))
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
                .driverId(100L)
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
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Cannot delete ride while it is ONGOING")));

        assertThat(rideRepository.existsById(ongoingRide.getRideId())).isTrue();
    }

    @Test
    @DisplayName("DELETE /api/rides/{id} on a SCHEDULED ride without bookings returns 204 No Content")
    void testDeleteScheduledRideReturns204() throws Exception {
        Ride scheduledRide = Ride.builder()
                .driverId(100L)
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
                .driverId(100L)
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
                .driverId(100L)
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
                .riderId(200L)
                .bookingStatus(BookingStatus.CONFIRMED)
                .bookingTime(LocalDateTime.now())
                .build();
        bookingRepository.save(booking);

        mockMvc.perform(delete("/api/rides/" + ride.getRideId())
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("cancel the ride instead")));
    }

    @Test
    @DisplayName("Phase 2: Search returns only SCHEDULED rides with future departure and excludes caller's own rides")
    void testSearchExcludesOwnRidesAndPastDepartures() throws Exception {
        // Driver 100's ride
        Ride ownRide = Ride.builder()
                .driverId(100L)
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
                .driverId(101L)
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
                .driverId(101L)
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

        // When Driver 100 searches, own ride is excluded -> only otherRide returned
        mockMvc.perform(get("/api/rides")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].driverId", is(101)));

        // When Rider 200 searches, both open rides (100 and 101) returned
        mockMvc.perform(get("/api/rides")
                        .header("Authorization", "Bearer " + riderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Driver 100 checks /api/rides/mine -> returns own ride
        mockMvc.perform(get("/api/rides/mine")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].driverId", is(100)));
    }

    @Test
    @DisplayName("Phase 2: Driver cancelling ride cascades CANCELLED status to all confirmed bookings")
    void testDriverCancelCascadesToConfirmedBookings() throws Exception {
        Ride ride = Ride.builder()
                .driverId(100L)
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
                .riderId(200L)
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
                .driverId(100L)
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
