package com.campuscommute.campuscommute.rides;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RideStatusValidatorTest {

    private RideStatusValidator validator;

    @BeforeEach
    void setUp() {
        validator = new RideStatusValidator();
    }

    @Test
    @DisplayName("Legal driver transition: SCHEDULED -> ONGOING")
    void testScheduledToOngoing() {
        assertThatCode(() -> validator.validateDriverTransition(RideStatus.SCHEDULED, RideStatus.ONGOING))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Legal driver transition: ONGOING -> COMPLETED")
    void testOngoingToCompleted() {
        assertThatCode(() -> validator.validateDriverTransition(RideStatus.ONGOING, RideStatus.COMPLETED))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Legal driver transition: SCHEDULED -> CANCELLED")
    void testScheduledToCancelled() {
        assertThatCode(() -> validator.validateDriverTransition(RideStatus.SCHEDULED, RideStatus.CANCELLED))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Legal driver transition: FULL -> ONGOING and FULL -> CANCELLED")
    void testFullTransitions() {
        assertThatCode(() -> validator.validateDriverTransition(RideStatus.FULL, RideStatus.ONGOING))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validateDriverTransition(RideStatus.FULL, RideStatus.CANCELLED))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Illegal driver transition: SCHEDULED -> COMPLETED directly throws InvalidRideStateException")
    void testScheduledToCompletedDirectlyFails() {
        assertThatThrownBy(() -> validator.validateDriverTransition(RideStatus.SCHEDULED, RideStatus.COMPLETED))
                .isInstanceOf(InvalidRideStateException.class)
                .hasMessageContaining("Illegal status transition from SCHEDULED to COMPLETED");
    }

    @Test
    @DisplayName("Illegal driver transition: ONGOING -> CANCELLED throws InvalidRideStateException")
    void testOngoingToCancelledFails() {
        assertThatThrownBy(() -> validator.validateDriverTransition(RideStatus.ONGOING, RideStatus.CANCELLED))
                .isInstanceOf(InvalidRideStateException.class)
                .hasMessageContaining("Illegal status transition from ONGOING to CANCELLED");
    }

    @Test
    @DisplayName("Illegal driver transition: COMPLETED -> any throws InvalidRideStateException")
    void testCompletedTransitionsFail() {
        assertThatThrownBy(() -> validator.validateDriverTransition(RideStatus.COMPLETED, RideStatus.SCHEDULED))
                .isInstanceOf(InvalidRideStateException.class);
        assertThatThrownBy(() -> validator.validateDriverTransition(RideStatus.COMPLETED, RideStatus.ONGOING))
                .isInstanceOf(InvalidRideStateException.class);
    }

    @Test
    @DisplayName("Legal internal transitions: SCHEDULED <-> FULL")
    void testInternalTransitions() {
        assertThatCode(() -> validator.validateInternalTransition(RideStatus.SCHEDULED, RideStatus.FULL))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validateInternalTransition(RideStatus.FULL, RideStatus.SCHEDULED))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Illegal internal transitions throw InvalidRideStateException")
    void testIllegalInternalTransitions() {
        assertThatThrownBy(() -> validator.validateInternalTransition(RideStatus.SCHEDULED, RideStatus.COMPLETED))
                .isInstanceOf(InvalidRideStateException.class);
        assertThatThrownBy(() -> validator.validateInternalTransition(RideStatus.ONGOING, RideStatus.FULL))
                .isInstanceOf(InvalidRideStateException.class);
    }

    @Test
    @DisplayName("validateCanDelete blocks ONGOING rides")
    void testValidateCanDeleteBlocksOngoing() {
        Ride ongoingRide = Ride.builder()
                .rideStatus(RideStatus.ONGOING)
                .build();

        assertThatThrownBy(() -> validator.validateCanDelete(ongoingRide))
                .isInstanceOf(InvalidRideStateException.class)
                .hasMessageContaining("Cannot delete ride while it is ONGOING");

        Ride scheduledRide = Ride.builder()
                .rideStatus(RideStatus.SCHEDULED)
                .build();
        assertThatCode(() -> validator.validateCanDelete(scheduledRide))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateCanModify blocks ONGOING rides")
    void testValidateCanModifyBlocksOngoing() {
        Ride ongoingRide = Ride.builder()
                .rideStatus(RideStatus.ONGOING)
                .build();

        assertThatThrownBy(() -> validator.validateCanModify(ongoingRide))
                .isInstanceOf(InvalidRideStateException.class)
                .hasMessageContaining("Cannot modify ride while it is ONGOING");

        Ride scheduledRide = Ride.builder()
                .rideStatus(RideStatus.SCHEDULED)
                .build();
        assertThatCode(() -> validator.validateCanModify(scheduledRide))
                .doesNotThrowAnyException();
    }
}
