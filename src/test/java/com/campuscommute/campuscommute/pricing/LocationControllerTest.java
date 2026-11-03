package com.campuscommute.campuscommute.pricing;

import com.campuscommute.campuscommute.pricing.dto.LocationUpdate;
import com.campuscommute.campuscommute.rides.Ride;
import com.campuscommute.campuscommute.rides.RideRepository;
import com.campuscommute.campuscommute.rides.RideStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LocationControllerTest {

    @Mock
    private RideRepository rideRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private LocationController locationController;

    private LocationUpdate sampleLocation;

    @BeforeEach
    void setUp() {
        sampleLocation = new LocationUpdate(13.0334, 80.1805, 45.0, 180.0, System.currentTimeMillis());
    }

    @Test
    @DisplayName("Location message published while ride is SCHEDULED is NOT broadcast")
    void updateLocation_WhenRideIsScheduled_DoesNotBroadcast() {
        Long rideId = 1L;
        Ride scheduledRide = Ride.builder()
                .rideId(rideId)
                .rideStatus(RideStatus.SCHEDULED)
                .build();

        when(rideRepository.findById(rideId)).thenReturn(Optional.of(scheduledRide));

        locationController.updateLocation(rideId, sampleLocation);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("Location message published while ride is ONGOING IS broadcast to scoped topic")
    void updateLocation_WhenRideIsOngoing_BroadcastsToRideTopic() {
        Long rideId = 2L;
        Ride ongoingRide = Ride.builder()
                .rideId(rideId)
                .rideStatus(RideStatus.ONGOING)
                .build();

        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ongoingRide));

        locationController.updateLocation(rideId, sampleLocation);

        verify(messagingTemplate).convertAndSend(eq("/topic/ride/2/location"), eq(sampleLocation));
    }

    @Test
    @DisplayName("Location message for non-existent ride is discarded")
    void updateLocation_WhenRideNotFound_DoesNotBroadcast() {
        Long rideId = 999L;
        when(rideRepository.findById(rideId)).thenReturn(Optional.empty());

        locationController.updateLocation(rideId, sampleLocation);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }
}

