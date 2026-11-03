package com.campuscommute.campuscommute.rides;

import com.campuscommute.campuscommute.auth.StudentDirectory;
import com.campuscommute.campuscommute.auth.StudentDirectoryRepository;
import com.campuscommute.campuscommute.auth.User;
import com.campuscommute.campuscommute.auth.UserRepository;
import com.campuscommute.campuscommute.bookings.Booking;
import com.campuscommute.campuscommute.bookings.BookingRepository;
import com.campuscommute.campuscommute.bookings.BookingStatus;
import com.campuscommute.campuscommute.pricing.FuelRateProvider;
import com.campuscommute.campuscommute.rides.dto.CreateRideRequest;
import com.campuscommute.campuscommute.rides.dto.RideResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional
public class RideService {

    private final RideRepository rideRepository;
    private final RideStatusValidator rideStatusValidator;
    private final GoogleRoutesClient googleRoutesClient;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final StudentDirectoryRepository studentDirectoryRepository;
    private final FuelRateProvider fuelRateProvider;

    @Value("${rides.min-gap-minutes:60}")
    private long minGapMinutes = 60;

    @Value("${rides.search.proximity-radius-km:5.0}")
    private double proximityRadiusKm = 5.0;

    public RideService(
            RideRepository rideRepository,
            RideStatusValidator rideStatusValidator,
            GoogleRoutesClient googleRoutesClient,
            BookingRepository bookingRepository,
            UserRepository userRepository,
            StudentDirectoryRepository studentDirectoryRepository,
            FuelRateProvider fuelRateProvider
    ) {
        this.rideRepository = rideRepository;
        this.rideStatusValidator = rideStatusValidator;
        this.googleRoutesClient = googleRoutesClient;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.studentDirectoryRepository = studentDirectoryRepository;
        this.fuelRateProvider = fuelRateProvider;
    }

    public RideResponse createRide(Long driverId, CreateRideRequest request) {
        if (driverId == null) {
            throw new IllegalArgumentException("Driver ID must not be null");
        }

        // Race safety: Acquire pessimistic write lock on the driver's User record.
        // This serializes concurrent ride creation requests for the same driver,
        // preventing two simultaneous requests from bypassing the departure overlap check.
        userRepository.findByIdForUpdate(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Driver not found with ID: " + driverId));

        // Decision D5: Rides have a pickup AND a drop, both with coordinates.
        // At least one end must be within 500m of Easwari Engineering College (EEC).
        double pickupToEecKm = GoogleRoutesClient.computeStraightDistanceKm(
                request.pickupLat(), request.pickupLng(), Ride.EEC_LAT, Ride.EEC_LNG);
        double dropToEecKm = GoogleRoutesClient.computeStraightDistanceKm(
                request.dropLat(), request.dropLng(), Ride.EEC_LAT, Ride.EEC_LNG);
        if (pickupToEecKm > 0.5 && dropToEecKm > 0.5) {
            throw new IllegalArgumentException("One end of the ride must be Easwari Engineering College");
        }

        // Decision D6: Overlap check - driver's SCHEDULED or ONGOING rides must have departure
        // times at least min-gap-minutes apart. Cancelled and completed rides do not block a slot.
        List<Ride> activeRides = rideRepository.findActiveRidesByDriverId(driverId);
        for (Ride existing : activeRides) {
            if (existing.getDepartureTime() != null && request.departureTime() != null) {
                LocalDateTime existingTime = existing.getDepartureTime().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
                LocalDateTime requestTime = request.departureTime().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
                long minutesBetween = Math.abs(Duration.between(existingTime, requestTime).toMinutes());
                if (minutesBetween < minGapMinutes) {
                    String formattedTime = existing.getDepartureTime().format(DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH));
                    throw new RideOverlapException(String.format(
                            "You already have a ride at %s. Rides must be at least %d minutes apart.",
                            formattedTime,
                            minGapMinutes
                    ));
                }
            }
        }

        String destination = (request.destination() != null && !request.destination().isBlank())
                ? request.destination()
                : Ride.EEC_NAME;

        // Distance (D4) is computed from pickup to drop, not to a fixed EEC point
        BigDecimal totalDistanceKm = googleRoutesClient.computeDistanceKm(
                request.pickupLat(),
                request.pickupLng(),
                request.dropLat(),
                request.dropLng(),
                request.startLocation(),
                destination,
                request.vehicleType()
        );

        // Fallback for mocks/tests that stub 3-param computeDistanceKm
        if (totalDistanceKm == null || totalDistanceKm.compareTo(BigDecimal.ZERO) <= 0) {
            totalDistanceKm = googleRoutesClient.computeDistanceKm(
                    request.startLocation(),
                    destination,
                    request.vehicleType()
            );
        }

        // Fallback to Haversine straight-line * 1.25 if still 0 or null
        if ((totalDistanceKm == null || totalDistanceKm.compareTo(BigDecimal.ZERO) <= 0)
                && request.pickupLat() != null && request.pickupLng() != null
                && request.dropLat() != null && request.dropLng() != null) {
            totalDistanceKm = GoogleRoutesClient.computeHaversineDistanceKm(
                    request.pickupLat(),
                    request.pickupLng(),
                    request.dropLat(),
                    request.dropLng()
            );
        }

        // Never store 0: reject a ride whose distance is <= 0 (Decision D4)
        if (totalDistanceKm == null || totalDistanceKm.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Ride distance must be greater than zero. Pickup location and drop location cannot be identical.");
        }

        Ride ride = Ride.builder()
                .driverId(driverId)
                .startLocation(request.startLocation())
                .destination(destination)
                .vehicleType(request.vehicleType())
                .vehicleModel(request.vehicleModel())
                .vehiclePlate(request.vehiclePlate())
                .departureTime(request.departureTime())
                .pickupLat(request.pickupLat())
                .pickupLng(request.pickupLng())
                .dropLat(request.dropLat())
                .dropLng(request.dropLng())
                .notes(request.notes())
                .mileageKmpl(request.mileageKmpl())
                .totalDistanceKm(totalDistanceKm)
                .availableSeats(request.availableSeats())
                .rideStatus(RideStatus.SCHEDULED)
                .build();

        Ride savedRide = rideRepository.save(ride);
        return toRideResponse(savedRide);
    }

    @Transactional(readOnly = true)
    public List<RideResponse> getRides(
            Long callerId,
            String start,
            String destination,
            Double pickupLat,
            Double pickupLng,
            Double dropLat,
            Double dropLng
    ) {
        String filterStart = (start != null && !start.isBlank()) ? start.trim() : null;
        String filterDest = (destination != null && !destination.isBlank()) ? destination.trim() : null;

        // Search returns only SCHEDULED rides with available seats and future departure, excluding caller's own
        List<Ride> rides = rideRepository.searchOpenRides(
                callerId,
                LocalDateTime.now(),
                filterStart,
                filterDest
        );

        boolean hasProximityCoords = (pickupLat != null && pickupLng != null && dropLat != null && dropLng != null);
        if (hasProximityCoords) {
            List<RideResponse> filtered = new ArrayList<>();
            for (Ride ride : rides) {
                if (ride.getPickupLat() == null || ride.getPickupLng() == null ||
                    ride.getDropLat() == null || ride.getDropLng() == null) {
                    continue; // Skip legacy rides missing drop coordinates
                }
                double pickupDist = GoogleRoutesClient.computeStraightDistanceKm(
                        pickupLat, pickupLng, ride.getPickupLat(), ride.getPickupLng());
                double dropDist = GoogleRoutesClient.computeStraightDistanceKm(
                        dropLat, dropLng, ride.getDropLat(), ride.getDropLng());

                if (pickupDist <= proximityRadiusKm && dropDist <= proximityRadiusKm) {
                    double roundedPickupDist = BigDecimal.valueOf(pickupDist)
                            .setScale(1, RoundingMode.HALF_UP)
                            .doubleValue();
                    filtered.add(toRideResponse(ride, roundedPickupDist));
                }
            }
            // Sort by pickup distance then departure time
            filtered.sort(Comparator.comparing(RideResponse::pickupDistanceKm)
                    .thenComparing(RideResponse::departureTime, Comparator.nullsLast(Comparator.naturalOrder())));
            return filtered;
        }

        return rides.stream()
                .map(this::toRideResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RideResponse> getRides(Long callerId, String start, String destination) {
        return getRides(callerId, start, destination, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<RideResponse> getRides(String start, String destination) {
        return getRides(null, start, destination, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<RideResponse> getMyRides(Long driverId) {
        List<Ride> rides = rideRepository.findByDriverIdOrderByDepartureTimeDesc(driverId);
        return rides.stream()
                .map(this::toRideResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RideResponse getRideById(Long rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));
        return toRideResponse(ride);
    }

    public RideResponse updateRideStatus(Long rideId, Long driverId, RideStatus targetStatus) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));

        // OWNERSHIP: PATCH status must verify ride.driverId == current user, else 403
        if (driverId != null && !ride.getDriverId().equals(driverId)) {
            throw new AccessDeniedException("You are not authorized to update the status of this ride");
        }

        rideStatusValidator.validateDriverTransition(ride.getRideStatus(), targetStatus);

        // When completing a ride, snapshot the fuel rate used into Ride.fuelRatePerLitre
        if (targetStatus == RideStatus.COMPLETED) {
            ride.setFuelRatePerLitre(fuelRateProvider.getCurrentRatePerLitre());
        }

        // When a driver cancels a ride, set all its CONFIRMED bookings to CANCELLED in the same transaction
        if (targetStatus == RideStatus.CANCELLED) {
            List<Booking> confirmedBookings = bookingRepository.findByRideIdAndBookingStatus(rideId, BookingStatus.CONFIRMED);
            for (Booking booking : confirmedBookings) {
                booking.setBookingStatus(BookingStatus.CANCELLED);
            }
            bookingRepository.saveAll(confirmedBookings);
        }

        ride.setRideStatus(targetStatus);
        Ride updatedRide = rideRepository.save(ride);
        return toRideResponse(updatedRide);
    }

    public RideResponse updateRideStatus(Long rideId, RideStatus targetStatus) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));
        return updateRideStatus(rideId, ride.getDriverId(), targetStatus);
    }

    public void deleteRide(Long rideId, Long driverId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));

        // OWNERSHIP: DELETE must verify ride.driverId == current user, else 403
        if (driverId != null && !ride.getDriverId().equals(driverId)) {
            throw new AccessDeniedException("You are not authorized to delete this ride");
        }

        // Delete is allowed only when the ride has no CONFIRMED bookings (else 409)
        if (bookingRepository.existsByRideIdAndBookingStatus(rideId, BookingStatus.CONFIRMED)) {
            throw new InvalidRideStateException("Cannot delete ride with confirmed bookings; cancel the ride instead");
        }

        rideStatusValidator.validateCanDelete(ride);
        rideRepository.delete(ride);
    }

    public void deleteRide(Long rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));
        deleteRide(rideId, ride.getDriverId());
    }

    private RideResponse toRideResponse(Ride ride) {
        return toRideResponse(ride, null);
    }

    private RideResponse toRideResponse(Ride ride, Double pickupDistanceKm) {
        String driverName = null;
        String department = null;
        if (ride.getDriverId() != null) {
            User driver = userRepository.findById(ride.getDriverId()).orElse(null);
            if (driver != null) {
                StudentDirectory student = studentDirectoryRepository.findById(driver.getRegisterNumber()).orElse(null);
                if (student != null) {
                    driverName = student.getFullName();
                    department = student.getDepartment();
                }
            }
        }
        return RideResponse.from(ride, driverName, department, pickupDistanceKm);
    }
}
