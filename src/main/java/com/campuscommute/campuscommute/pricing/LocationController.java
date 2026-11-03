package com.campuscommute.campuscommute.pricing;

import com.campuscommute.campuscommute.pricing.dto.LocationUpdate;
import com.campuscommute.campuscommute.rides.Ride;
import com.campuscommute.campuscommute.rides.RideRepository;
import com.campuscommute.campuscommute.rides.RideStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class LocationController {

    private static final Logger log = LoggerFactory.getLogger(LocationController.class);

    private final RideRepository rideRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public LocationController(RideRepository rideRepository, SimpMessagingTemplate messagingTemplate) {
        this.rideRepository = rideRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Receives GPS coordinates from driver at /app/ride/{rideId}/location.
     * Broadcasts to subscribers at /topic/ride/{rideId}/location ONLY if the ride is ONGOING.
     */
    @MessageMapping("/ride/{rideId}/location")
    public void updateLocation(
            @DestinationVariable("rideId") Long rideId,
            LocationUpdate locationUpdate
    ) {
        Ride ride = rideRepository.findById(rideId).orElse(null);
        if (ride == null) {
            log.warn("Discarding location update for non-existent rideId: {}", rideId);
            return;
        }

        if (ride.getRideStatus() != RideStatus.ONGOING) {
            log.info("Ignoring location update for ride {}: status is {}, not ONGOING",
                    rideId, ride.getRideStatus());
            return;
        }

        String destination = "/topic/ride/" + rideId + "/location";
        messagingTemplate.convertAndSend(destination, locationUpdate);
    }
}

