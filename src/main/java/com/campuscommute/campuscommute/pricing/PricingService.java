package com.campuscommute.campuscommute.pricing;

import com.campuscommute.campuscommute.auth.StudentDirectory;
import com.campuscommute.campuscommute.auth.StudentDirectoryRepository;
import com.campuscommute.campuscommute.auth.User;
import com.campuscommute.campuscommute.auth.UserRepository;
import com.campuscommute.campuscommute.bookings.BookingRepository;
import com.campuscommute.campuscommute.bookings.BookingStatus;
import com.campuscommute.campuscommute.pricing.dto.PricingResponse;
import com.campuscommute.campuscommute.pricing.dto.SettlementResponse;
import com.campuscommute.campuscommute.rides.InvalidRideStateException;
import com.campuscommute.campuscommute.rides.Ride;
import com.campuscommute.campuscommute.rides.RideNotFoundException;
import com.campuscommute.campuscommute.rides.RideRepository;
import com.campuscommute.campuscommute.rides.RideStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class PricingService {

    private final RideRepository rideRepository;
    private final BookingRepository bookingRepository;
    private final FuelRateProvider fuelRateProvider;
    private final UserRepository userRepository;
    private final StudentDirectoryRepository studentDirectoryRepository;

    public PricingService(
            RideRepository rideRepository,
            BookingRepository bookingRepository,
            FuelRateProvider fuelRateProvider,
            UserRepository userRepository,
            StudentDirectoryRepository studentDirectoryRepository
    ) {
        this.rideRepository = rideRepository;
        this.bookingRepository = bookingRepository;
        this.fuelRateProvider = fuelRateProvider;
        this.userRepository = userRepository;
        this.studentDirectoryRepository = studentDirectoryRepository;
    }

    @Transactional(readOnly = true)
    public PricingResponse calculate(Long rideId) {
        return calculate(rideId, null);
    }

    @Transactional(readOnly = true)
    public PricingResponse calculate(Long rideId, Long currentUserId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException("Ride not found with id: " + rideId));

        // Enforce participant authorization: only driver or confirmed riders can view pricing
        if (currentUserId != null) {
            boolean isDriver = ride.getDriverId().equals(currentUserId);
            boolean isConfirmedPassenger = bookingRepository.existsByRideIdAndRiderIdAndBookingStatus(rideId, currentUserId, BookingStatus.CONFIRMED);
            if (!isDriver && !isConfirmedPassenger) {
                throw new AccessDeniedException("Access denied: You are neither the driver nor a confirmed passenger of this ride.");
            }
        }

        if (ride.getRideStatus() != RideStatus.COMPLETED) {
            throw new RideNotCompletedException(
                    "Ride " + rideId + " is not COMPLETED yet (current status: " + ride.getRideStatus() + "). "
                            + "Pricing is calculated dynamically only upon ride completion."
            );
        }

        BigDecimal distance = ride.getTotalDistanceKm();
        if (distance == null || distance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRideStateException("Cannot calculate pricing for ride " + rideId + " with invalid distance: " + distance);
        }

        BigDecimal mileage = ride.getMileageKmpl();
        if (mileage == null || mileage.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Ride mileage must be greater than zero to calculate pricing.");
        }

        // Use ride.fuelRatePerLitre snapshot if set on completion, otherwise fallback to current provider rate
        BigDecimal ratePerLitre = ride.getFuelRatePerLitre() != null
                ? ride.getFuelRatePerLitre()
                : fuelRateProvider.getCurrentRatePerLitre();

        int confirmedRiders = bookingRepository.findByRideIdAndBookingStatus(rideId, BookingStatus.CONFIRMED).size();
        int occupantCount = confirmedRiders + 1; // 1 driver + confirmed passengers

        BigDecimal totalFuelCost = distance
                .multiply(ratePerLitre)
                .divide(mileage, 2, RoundingMode.HALF_UP);

        BigDecimal perPersonShare = totalFuelCost
                .divide(BigDecimal.valueOf(occupantCount), 2, RoundingMode.HALF_UP);

        return new PricingResponse(
                rideId,
                distance,
                mileage,
                ratePerLitre,
                totalFuelCost,
                occupantCount,
                perPersonShare
        );
    }

    @Transactional(readOnly = true)
    public SettlementResponse getSettlement(Long rideId) {
        return getSettlement(rideId, null);
    }

    @Transactional(readOnly = true)
    public SettlementResponse getSettlement(Long rideId, Long currentUserId) {
        PricingResponse pricing = calculate(rideId, currentUserId);

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException("Ride not found with id: " + rideId));

        User driver = userRepository.findById(ride.getDriverId()).orElse(null);
        String driverPhone = driver != null ? driver.getPhoneNumber() : null;
        String driverUpiId = driver != null ? driver.getUpiId() : null;

        String upiLink = null;
        if (driverUpiId != null && !driverUpiId.isBlank()) {
            String driverName = "Driver";
            if (driver != null && driver.getRegisterNumber() != null) {
                driverName = studentDirectoryRepository.findById(driver.getRegisterNumber())
                        .map(StudentDirectory::getFullName)
                        .orElse("Driver");
            }

            String encodedPa = URLEncoder.encode(driverUpiId, StandardCharsets.UTF_8);
            String encodedPn = URLEncoder.encode(driverName, StandardCharsets.UTF_8).replace("+", "%20");
            String encodedTn = URLEncoder.encode("CampusCommute Ride " + rideId, StandardCharsets.UTF_8).replace("+", "%20");

            upiLink = String.format(
                    "upi://pay?pa=%s&pn=%s&am=%s&cu=INR&tn=%s",
                    encodedPa,
                    encodedPn,
                    pricing.perPersonShare().toPlainString(),
                    encodedTn
            );
        }

        return new SettlementResponse(
                rideId,
                ride.getDriverId(),
                driverPhone,
                pricing.totalDistanceKm(),
                pricing.mileageKmpl(),
                pricing.ratePerLitre(),
                pricing.totalFuelCost(),
                pricing.occupantCount(),
                pricing.perPersonShare(),
                upiLink,
                upiLink
        );
    }
}
