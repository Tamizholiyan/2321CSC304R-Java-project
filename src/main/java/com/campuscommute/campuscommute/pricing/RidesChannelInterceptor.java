package com.campuscommute.campuscommute.pricing;

import com.campuscommute.campuscommute.auth.JwtUtil;
import com.campuscommute.campuscommute.bookings.BookingRepository;
import com.campuscommute.campuscommute.bookings.BookingStatus;
import com.campuscommute.campuscommute.rides.Ride;
import com.campuscommute.campuscommute.rides.RideRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RidesChannelInterceptor implements ChannelInterceptor {

    private static final Pattern SEND_LOCATION_PATTERN = Pattern.compile("^/app/ride/(\\d+)/location/?$");
    private static final Pattern SUBSCRIBE_LOCATION_PATTERN = Pattern.compile("^/topic/ride/(\\d+)/location/?$");

    private final JwtUtil jwtUtil;
    private final RideRepository rideRepository;
    private final BookingRepository bookingRepository;

    public RidesChannelInterceptor(
            JwtUtil jwtUtil,
            RideRepository rideRepository,
            BookingRepository bookingRepository
    ) {
        this.jwtUtil = jwtUtil;
        this.rideRepository = rideRepository;
        this.bookingRepository = bookingRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        // 1. STOMP CONNECT: Validate Authorization Bearer token and authenticate user
        if (StompCommand.CONNECT.equals(command)) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new AccessDeniedException("Missing or invalid Authorization header in STOMP CONNECT frame");
            }
            String token = authHeader.substring(7);
            if (!jwtUtil.validateToken(token)) {
                throw new AccessDeniedException("Invalid or expired JWT token in STOMP CONNECT frame");
            }
            Long userId = jwtUtil.extractUserId(token);
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
            );
            if (accessor.isMutable()) {
                accessor.setUser(auth);
            } else {
                StompHeaderAccessor mutableAccessor = StompHeaderAccessor.wrap(message);
                mutableAccessor.setUser(auth);
                return MessageBuilder.createMessage(message.getPayload(), mutableAccessor.getMessageHeaders());
            }
        }

        // 2. STOMP SEND: Only the ride's driver is allowed to broadcast location updates
        else if (StompCommand.SEND.equals(command)) {
            String destination = accessor.getDestination();
            if (destination != null) {
                Matcher matcher = SEND_LOCATION_PATTERN.matcher(destination);
                if (matcher.matches()) {
                    Long userId = extractUserId(accessor);
                    if (userId == null) {
                        throw new AccessDeniedException("Unauthenticated: cannot send location updates");
                    }
                    Long rideId = Long.parseLong(matcher.group(1));
                    Ride ride = rideRepository.findById(rideId).orElse(null);
                    if (ride == null || !ride.getDriverId().equals(userId)) {
                        throw new AccessDeniedException("Only the driver of ride " + rideId + " is allowed to broadcast location updates.");
                    }
                }
            }
        }

        // 3. STOMP SUBSCRIBE: Only driver or confirmed passengers of the ride can track location
        else if (StompCommand.SUBSCRIBE.equals(command)) {
            String destination = accessor.getDestination();
            if (destination != null) {
                Matcher matcher = SUBSCRIBE_LOCATION_PATTERN.matcher(destination);
                if (matcher.matches()) {
                    Long userId = extractUserId(accessor);
                    if (userId == null) {
                        throw new AccessDeniedException("Unauthenticated: cannot subscribe to ride location");
                    }
                    Long rideId = Long.parseLong(matcher.group(1));
                    Ride ride = rideRepository.findById(rideId).orElse(null);
                    if (ride == null) {
                        throw new AccessDeniedException("Ride " + rideId + " not found.");
                    }
                    boolean isDriver = ride.getDriverId().equals(userId);
                    boolean isConfirmedPassenger = bookingRepository.existsByRideIdAndRiderIdAndBookingStatus(
                            rideId, userId, BookingStatus.CONFIRMED
                    );
                    if (!isDriver && !isConfirmedPassenger) {
                        throw new AccessDeniedException("Only the driver or confirmed passengers of ride " + rideId + " can track location.");
                    }
                }
            }
        }

        return message;
    }

    private Long extractUserId(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof Long id) {
            return id;
        }
        if (principal != null) {
            try {
                return Long.valueOf(principal.getName());
            } catch (NumberFormatException ignored) {
            }
        }
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.validateToken(token)) {
                return jwtUtil.extractUserId(token);
            }
        }
        return null;
    }
}

