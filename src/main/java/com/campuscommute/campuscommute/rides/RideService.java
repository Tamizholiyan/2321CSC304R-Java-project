package com.campuscommute.campuscommute.rides;

import com.campuscommute.campuscommute.rides.dto.CreateRideRequest;
import com.campuscommute.campuscommute.rides.dto.RideResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class RideService {

    private final RideRepository rideRepository;
    private final RideStatusValidator rideStatusValidator;
    private final GoogleRoutesClient googleRoutesClient;

    public RideService(
            RideRepository rideRepository,
            RideStatusValidator rideStatusValidator,
            GoogleRoutesClient googleRoutesClient
    ) {
        this.rideRepository = rideRepository;
        this.rideStatusValidator = rideStatusValidator;
        this.googleRoutesClient = googleRoutesClient;
    }

    public RideResponse createRide(Long driverId, CreateRideRequest request) {
        if (driverId == null) {
            throw new IllegalArgumentException("Driver ID must not be null");
        }

        // Compute total distance server-side using Google Routes API
        BigDecimal totalDistanceKm = googleRoutesClient.computeDistanceKm(
                request.startLocation(),
                request.destination(),
                request.vehicleType()
        );

        Ride ride = Ride.builder()
                .driverId(driverId)
                .startLocation(request.startLocation())
                .destination(request.destination())
                .vehicleType(request.vehicleType())
                .mileageKmpl(request.mileageKmpl())
                .totalDistanceKm(totalDistanceKm)
                .availableSeats(request.availableSeats())
                .rideStatus(RideStatus.SCHEDULED)
                .build();

        Ride savedRide = rideRepository.save(ride);
        return RideResponse.from(savedRide);
    }

    @Transactional(readOnly = true)
    public List<RideResponse> getRides(String start, String destination) {
        String filterStart = (start != null && !start.isBlank()) ? start.trim() : null;
        String filterDest = (destination != null && !destination.isBlank()) ? destination.trim() : null;

        List<Ride> rides = rideRepository.searchRides(filterStart, filterDest);
        return rides.stream()
                .map(RideResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public RideResponse getRideById(Long rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));
        return RideResponse.from(ride);
    }

    public RideResponse updateRideStatus(Long rideId, RideStatus targetStatus) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));

        rideStatusValidator.validateDriverTransition(ride.getRideStatus(), targetStatus);
        ride.setRideStatus(targetStatus);

        Ride updatedRide = rideRepository.save(ride);
        return RideResponse.from(updatedRide);
    }

    public void deleteRide(Long rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));

        rideStatusValidator.validateCanDelete(ride);
        rideRepository.delete(ride);
    }
}
