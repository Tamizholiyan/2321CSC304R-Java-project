package com.campuscommute.campuscommute.rides;

import com.campuscommute.campuscommute.auth.Role;
import com.campuscommute.campuscommute.auth.StudentDirectoryRepository;
import com.campuscommute.campuscommute.auth.User;
import com.campuscommute.campuscommute.auth.UserRepository;
import com.campuscommute.campuscommute.bookings.BookingRepository;
import com.campuscommute.campuscommute.pricing.FuelRateProvider;
import com.campuscommute.campuscommute.rides.dto.CreateRideRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TimezoneDepartureTest {

    private static final ZoneId KOLKATA_ZONE = ZoneId.of("Asia/Kolkata");

    @Mock
    private RideRepository rideRepository;
    @Mock
    private RideStatusValidator rideStatusValidator;
    @Mock
    private GoogleRoutesClient googleRoutesClient;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StudentDirectoryRepository studentDirectoryRepository;
    @Mock
    private FuelRateProvider fuelRateProvider;
    @Mock
    private ScheduleConflictService scheduleConflictService;

    // Fixed instant: 2026-11-04 12:00:00 UTC = 2026-11-04 17:30:00 IST (Kolkata)
    private final Instant fixedInstant = Instant.parse("2026-11-04T12:00:00Z");
    private final Clock fixedClock = Clock.fixed(fixedInstant, KOLKATA_ZONE);

    private RideService rideService;

    @BeforeEach
    void setUp() {
        rideService = new RideService(
                rideRepository,
                rideStatusValidator,
                googleRoutesClient,
                bookingRepository,
                userRepository,
                studentDirectoryRepository,
                fuelRateProvider,
                scheduleConflictService,
                fixedClock
        );
    }

    @Test
    @DisplayName("Phase 14: Departure 1 hour ahead of Kolkata time is accepted")
    void testDepartureOneHourAheadOfKolkataTimeIsAccepted() {
        Long driverId = 1L;
        User driver = new User("310621104001", "9876543210", "pass", Role.DRIVER);
        driver.setUserId(driverId);

        when(userRepository.findByIdForUpdate(driverId)).thenReturn(Optional.of(driver));
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(googleRoutesClient.computeDistanceKm(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString(), anyString(), any()))
                .thenReturn(new BigDecimal("12.5"));

        when(rideRepository.save(any(Ride.class))).thenAnswer(invocation -> {
            Ride r = invocation.getArgument(0);
            r.setRideId(101L);
            return r;
        });

        // Current Kolkata time is 17:30:00; 1 hour ahead is 18:30:00
        LocalDateTime currentKolkataTime = LocalDateTime.now(fixedClock);
        assertThat(currentKolkataTime).isEqualTo(LocalDateTime.of(2026, 11, 4, 17, 30, 0));

        LocalDateTime departureOneHourAhead = currentKolkataTime.plusHours(1);

        CreateRideRequest request = new CreateRideRequest(
                "EEC Campus",
                "Guindy Metro",
                VehicleType.FOUR_WHEELER,
                "Swift",
                "TN09CC1234",
                departureOneHourAhead,
                Ride.EEC_LAT,
                Ride.EEC_LNG,
                13.0067,
                80.2023,
                new BigDecimal("15.0"),
                3,
                "One hour ahead test"
        );

        var response = rideService.createRide(driverId, request);
        assertThat(response).isNotNull();
        assertThat(response.departureTime()).isEqualTo(departureOneHourAhead);
    }

    @Test
    @DisplayName("Phase 14: Departure 1 hour behind Kolkata time is rejected")
    void testDepartureOneHourBehindKolkataTimeIsRejected() {
        Long driverId = 1L;
        User driver = new User("310621104001", "9876543210", "pass", Role.DRIVER);
        driver.setUserId(driverId);

        when(userRepository.findByIdForUpdate(driverId)).thenReturn(Optional.of(driver));

        // Current Kolkata time is 17:30:00; 1 hour behind is 16:30:00
        LocalDateTime currentKolkataTime = LocalDateTime.now(fixedClock);
        LocalDateTime departureOneHourBehind = currentKolkataTime.minusHours(1);

        CreateRideRequest request = new CreateRideRequest(
                "EEC Campus",
                "Guindy Metro",
                VehicleType.FOUR_WHEELER,
                "Swift",
                "TN09CC1234",
                departureOneHourBehind,
                Ride.EEC_LAT,
                Ride.EEC_LNG,
                13.0067,
                80.2023,
                new BigDecimal("15.0"),
                3,
                "One hour behind test"
        );

        assertThatThrownBy(() -> rideService.createRide(driverId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Departure time must be in the future");
    }
}
