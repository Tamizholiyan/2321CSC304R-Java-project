package com.campuscommute.campuscommute.rides;

import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class RideStatusValidator {

    private static final Map<RideStatus, Set<RideStatus>> LEGAL_DRIVER_TRANSITIONS = Map.of(
            RideStatus.SCHEDULED, EnumSet.of(RideStatus.ONGOING, RideStatus.CANCELLED),
            RideStatus.FULL, EnumSet.of(RideStatus.ONGOING, RideStatus.CANCELLED),
            RideStatus.ONGOING, EnumSet.of(RideStatus.COMPLETED)
    );

    private static final Map<RideStatus, Set<RideStatus>> LEGAL_INTERNAL_TRANSITIONS = Map.of(
            RideStatus.SCHEDULED, EnumSet.of(RideStatus.FULL),
            RideStatus.FULL, EnumSet.of(RideStatus.SCHEDULED)
    );

    /**
     * Validates driver-initiated status transitions.
     * Legal transitions:
     * - SCHEDULED -> ONGOING -> COMPLETED
     * - SCHEDULED -> CANCELLED
     * - FULL -> ONGOING -> COMPLETED
     * - FULL -> CANCELLED
     */
    public void validateDriverTransition(RideStatus currentStatus, RideStatus targetStatus) {
        if (targetStatus == null) {
            throw new InvalidRideStateException("Target ride status cannot be null");
        }

        Set<RideStatus> allowed = LEGAL_DRIVER_TRANSITIONS.get(currentStatus);
        if (allowed == null || !allowed.contains(targetStatus)) {
            throw new InvalidRideStateException(
                    "Illegal status transition from " + currentStatus + " to " + targetStatus
            );
        }
    }

    /**
     * Validates internal transitions (e.g. seat booking flow).
     * Legal transitions:
     * - SCHEDULED <-> FULL
     */
    public void validateInternalTransition(RideStatus currentStatus, RideStatus targetStatus) {
        if (targetStatus == null) {
            throw new InvalidRideStateException("Target ride status cannot be null");
        }

        Set<RideStatus> allowed = LEGAL_INTERNAL_TRANSITIONS.get(currentStatus);
        if (allowed == null || !allowed.contains(targetStatus)) {
            throw new InvalidRideStateException(
                    "Illegal internal status transition from " + currentStatus + " to " + targetStatus
            );
        }
    }

    /**
     * Blocks any modification while ONGOING.
     */
    public void validateCanModify(Ride ride) {
        if (ride.getRideStatus() == RideStatus.ONGOING) {
            throw new InvalidRideStateException("Cannot modify ride while it is ONGOING");
        }
    }

    /**
     * Blocks deletion while ONGOING.
     */
    public void validateCanDelete(Ride ride) {
        if (ride.getRideStatus() == RideStatus.ONGOING) {
            throw new InvalidRideStateException("Cannot delete ride while it is ONGOING");
        }
    }
}
