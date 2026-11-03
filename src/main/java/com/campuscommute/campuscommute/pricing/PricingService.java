package com.campuscommute.campuscommute.pricing;

import com.campuscommute.campuscommute.auth.User;
import com.campuscommute.campuscommute.auth.UserRepository;
import com.campuscommute.campuscommute.bookings.BookingRepository;
import com.campuscommute.campuscommute.bookings.BookingStatus;
import com.campuscommute.campuscommute.pricing.dto.PricingResponse;
import com.campuscommute.campuscommute.pricing.dto.SettlementResponse;
import com.campuscommute.campuscommute.rides.Ride;
import com.campuscommute.campuscommute.rides.RideNotFoundException;
import com.campuscommute.campuscommute.rides.RideRepository;
import com.campuscommute.campuscommute.rides.RideStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PricingService {

    private final RideRepository rideRepository;
    private final BookingRepository bookingRepository;
    private final FuelRateProvider fuelRateProvider;
    private final UserRepository userRepository;

    public PricingService(
            RideRepository rideRepository,
            BookingRepository bookingRepository,
            FuelRateProvider fuelRateProvider,
            UserRepository userRepository
    ) {
        this.rideRepository = rideRepository;
        this.bookingRepository = bookingRepository;
        this.fuelRateProvider = fuelRateProvider;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PricingResponse calculate(Long rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException("Ride not found with id: " + rideId));

        if (ride.getRideStatus() != RideStatus.COMPLETED) {
            throw new RideNotCompletedException(
                    "Ride " + rideId + " is not COMPLETED yet (current status: " + ride.getRideStatus() + "). "
                            + "Pricing is calculated dynamically only upon ride completion."
            );
        }

        BigDecimal mileage = ride.getMileageKmpl();
        if (mileage == null || mileage.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Ride mileage must be greater than zero to calculate pricing.");
        }

        BigDecimal distance = ride.getTotalDistanceKm() != null ? ride.getTotalDistanceKm() : BigDecimal.ZERO;
        BigDecimal ratePerLitre = fuelRateProvider.getCurrentRatePerLitre();

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
        PricingResponse pricing = calculate(rideId);

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException("Ride not found with id: " + rideId));

        String driverPhone = userRepository.findById(ride.getDriverId())
                .map(User::getPhoneNumber)
                .orElse("9876543210");

        String upiId = driverPhone + "@upi";
        String upiDeepLink = String.format(
                "upi://pay?pa=%s&pn=Driver&am=%s&cu=INR&tn=CampusCommute Ride %d",
                upiId,
                pricing.perPersonShare().toPlainString(),
                rideId
        );

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
                upiDeepLink
        );
    }
}
