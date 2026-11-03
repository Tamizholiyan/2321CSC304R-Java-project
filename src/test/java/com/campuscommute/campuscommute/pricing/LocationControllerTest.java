package com.campuscommute.campuscommute.pricing;

import com.campuscommute.campuscommute.auth.JwtUtil;
import com.campuscommute.campuscommute.bookings.BookingRepository;
import com.campuscommute.campuscommute.bookings.BookingStatus;
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
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    private BookingRepository bookingRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private MessageChannel messageChannel;

    @InjectMocks
    private LocationController locationController;

    private RidesChannelInterceptor ridesChannelInterceptor;

    private LocationUpdate sampleLocation;

    @BeforeEach
    void setUp() {
        sampleLocation = new LocationUpdate(13.0334, 80.1805, 45.0, 180.0, System.currentTimeMillis());
        ridesChannelInterceptor = new RidesChannelInterceptor(jwtUtil, rideRepository, bookingRepository);
    }

    // ==========================================
    // CONTROLLER TESTS: BROADCAST LOGIC
    // ==========================================

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

    // ==========================================
    // INTERCEPTOR TESTS: STOMP CONNECT
    // ==========================================

    @Test
    @DisplayName("STOMP CONNECT without Authorization header throws AccessDeniedException")
    void interceptor_ConnectWithoutAuth_ThrowsAccessDenied() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () ->
                ridesChannelInterceptor.preSend(message, messageChannel));
    }

    @Test
    @DisplayName("STOMP CONNECT with invalid JWT token throws AccessDeniedException")
    void interceptor_ConnectWithInvalidJwt_ThrowsAccessDenied() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer invalid-jwt");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtUtil.validateToken("invalid-jwt")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () ->
                ridesChannelInterceptor.preSend(message, messageChannel));
    }

    @Test
    @DisplayName("STOMP CONNECT with valid JWT sets user in accessor")
    void interceptor_ConnectWithValidJwt_AuthenticatesUser() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer valid-jwt");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtUtil.validateToken("valid-jwt")).thenReturn(true);
        when(jwtUtil.extractUserId("valid-jwt")).thenReturn(42L);

        Message<?> result = ridesChannelInterceptor.preSend(message, messageChannel);
        assertNotNull(result);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNotNull(resultAccessor.getUser());
        assertEquals("42", resultAccessor.getUser().getName());
    }

    // ==========================================
    // INTERCEPTOR TESTS: STOMP SEND
    // ==========================================

    @Test
    @DisplayName("STOMP SEND to /app/ride/{id}/location by driver is allowed")
    void interceptor_SendByDriver_Allowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/ride/10/location");
        accessor.setUser(new UsernamePasswordAuthenticationToken(5L, null, Collections.emptyList()));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Ride ride = Ride.builder().rideId(10L).driverId(5L).build();
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));

        Message<?> result = ridesChannelInterceptor.preSend(message, messageChannel);
        assertNotNull(result);
    }

    @Test
    @DisplayName("STOMP SEND to /app/ride/{id}/location by non-driver throws AccessDeniedException")
    void interceptor_SendByNonDriver_ThrowsAccessDenied() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/ride/10/location");
        accessor.setUser(new UsernamePasswordAuthenticationToken(99L, null, Collections.emptyList()));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Ride ride = Ride.builder().rideId(10L).driverId(5L).build();
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));

        assertThrows(AccessDeniedException.class, () ->
                ridesChannelInterceptor.preSend(message, messageChannel));
    }

    // ==========================================
    // INTERCEPTOR TESTS: STOMP SUBSCRIBE
    // ==========================================

    @Test
    @DisplayName("STOMP SUBSCRIBE to /topic/ride/{id}/location by driver is allowed")
    void interceptor_SubscribeByDriver_Allowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/ride/10/location");
        accessor.setUser(new UsernamePasswordAuthenticationToken(5L, null, Collections.emptyList()));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Ride ride = Ride.builder().rideId(10L).driverId(5L).build();
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));

        Message<?> result = ridesChannelInterceptor.preSend(message, messageChannel);
        assertNotNull(result);
    }

    @Test
    @DisplayName("STOMP SUBSCRIBE to /topic/ride/{id}/location by confirmed passenger is allowed")
    void interceptor_SubscribeByConfirmedPassenger_Allowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/ride/10/location");
        accessor.setUser(new UsernamePasswordAuthenticationToken(20L, null, Collections.emptyList()));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Ride ride = Ride.builder().rideId(10L).driverId(5L).build();
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        when(bookingRepository.existsByRideIdAndRiderIdAndBookingStatus(10L, 20L, BookingStatus.CONFIRMED)).thenReturn(true);

        Message<?> result = ridesChannelInterceptor.preSend(message, messageChannel);
        assertNotNull(result);
    }

    @Test
    @DisplayName("STOMP SUBSCRIBE to /topic/ride/{id}/location by outsider throws AccessDeniedException")
    void interceptor_SubscribeByOutsider_ThrowsAccessDenied() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/ride/10/location");
        accessor.setUser(new UsernamePasswordAuthenticationToken(99L, null, Collections.emptyList()));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Ride ride = Ride.builder().rideId(10L).driverId(5L).build();
        when(rideRepository.findById(10L)).thenReturn(Optional.of(ride));
        when(bookingRepository.existsByRideIdAndRiderIdAndBookingStatus(10L, 99L, BookingStatus.CONFIRMED)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () ->
                ridesChannelInterceptor.preSend(message, messageChannel));
    }
}
