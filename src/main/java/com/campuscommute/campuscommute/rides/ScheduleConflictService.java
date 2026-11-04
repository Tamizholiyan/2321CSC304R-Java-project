package com.campuscommute.campuscommute.rides;

import com.campuscommute.campuscommute.bookings.BookingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Unified schedule conflict service (Decision D7).
 * A "commitment" is either:
 * (a) a ride the user drives with status SCHEDULED, FULL, or ONGOING, or
 * (b) a CONFIRMED booking by the user on a ride with status SCHEDULED, FULL, or ONGOING.
 *
 * A user cannot create a ride or book a ride whose departureTime is less than
 * schedule.window-minutes (default 90) away from any of their commitments.
 * Exactly 90 minutes apart is allowed.
 * Cancelled and completed rides, and cancelled bookings, are not commitments.
 */
@Service
@Transactional(readOnly = true)
public class ScheduleConflictService {

    public record Commitment(
            boolean isDrive, // true: ride driven by user; false: confirmed booking by user
            Long rideId,
            LocalDateTime departureTime
    ) {}

    private final RideRepository rideRepository;
    private final BookingRepository bookingRepository;
    private final int windowMinutes;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    public ScheduleConflictService(
            RideRepository rideRepository,
            BookingRepository bookingRepository,
            @Value("${schedule.window-minutes:90}") int windowMinutes
    ) {
        this.rideRepository = rideRepository;
        this.bookingRepository = bookingRepository;
        this.windowMinutes = windowMinutes;
    }

    public int getWindowMinutes() {
        return windowMinutes;
    }

    /**
     * Loads all active commitments for the user:
     * - Active rides driven by the user (SCHEDULED, FULL, ONGOING)
     * - Confirmed bookings on active rides (SCHEDULED, FULL, ONGOING)
     */
    public List<Commitment> getCommitments(Long userId) {
        List<Commitment> commitments = new ArrayList<>();
        if (userId == null) {
            return commitments;
        }

        // 1. Rides the user drives
        List<Ride> drives = rideRepository.findActiveRidesByDriverId(userId);
        for (Ride drive : drives) {
            if (drive.getDepartureTime() != null) {
                commitments.add(new Commitment(true, drive.getRideId(), drive.getDepartureTime()));
            }
        }

        // 2. Rides where the user has a confirmed booking
        List<Ride> bookedRides = bookingRepository.findActiveBookedRidesByRiderId(userId);
        for (Ride booked : bookedRides) {
            if (booked.getDepartureTime() != null) {
                commitments.add(new Commitment(false, booked.getRideId(), booked.getDepartureTime()));
            }
        }

        return commitments;
    }

    /**
     * Evaluates whether the given departureTime clashes with any existing commitments.
     * Returns the formatted conflict message if a clash exists, or null if free.
     */
    public String findConflictMessage(List<Commitment> commitments, LocalDateTime departureTime) {
        if (departureTime == null || commitments == null || commitments.isEmpty()) {
            return null;
        }

        LocalDateTime targetTime = departureTime.truncatedTo(ChronoUnit.MINUTES);

        for (Commitment commitment : commitments) {
            if (commitment.departureTime() == null) {
                continue;
            }
            LocalDateTime commTime = commitment.departureTime().truncatedTo(ChronoUnit.MINUTES);
            long diffMinutes = Math.abs(Duration.between(commTime, targetTime).toMinutes());

            // Clashes if difference in departure time is strictly less than windowMinutes
            if (diffMinutes < windowMinutes) {
                String formattedTime = commitment.departureTime().format(TIME_FORMATTER);
                if (commitment.isDrive()) {
                    return String.format(
                            "You already have a ride you are driving at %s. Rides must be at least %d minutes apart.",
                            formattedTime,
                            windowMinutes
                    );
                } else {
                    return String.format(
                            "You already have a booking at %s. Rides must be at least %d minutes apart.",
                            formattedTime,
                            windowMinutes
                    );
                }
            }
        }

        return null;
    }

    /**
     * Asserts that the given user has no conflicting commitments around departureTime.
     * Throws RideOverlapException (409 Conflict) if a conflict is detected.
     */
    public void assertFree(Long userId, LocalDateTime departureTime) {
        if (userId == null || departureTime == null) {
            return;
        }

        List<Commitment> commitments = getCommitments(userId);
        String message = findConflictMessage(commitments, departureTime);
        if (message != null) {
            throw new RideOverlapException(message);
        }
    }
}

