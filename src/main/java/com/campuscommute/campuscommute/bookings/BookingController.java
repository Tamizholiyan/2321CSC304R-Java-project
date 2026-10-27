package com.campuscommute.campuscommute.bookings;

import com.campuscommute.campuscommute.bookings.dto.BookingResponse;
import com.campuscommute.campuscommute.bookings.dto.CreateBookingRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/api/bookings")
    @PreAuthorize("hasAnyAuthority('ROLE_RIDER', 'ROLE_BOTH')")
    public ResponseEntity<BookingResponse> bookRide(
            @AuthenticationPrincipal Long riderId,
            @RequestParam(value = "rideId", required = false) Long queryRideId,
            @RequestBody(required = false) CreateBookingRequest request
    ) {
        Long rideId = queryRideId != null ? queryRideId : (request != null ? request.rideId() : null);
        if (rideId == null) {
            throw new IllegalArgumentException("rideId must be provided as a query parameter or in the request body");
        }
        BookingResponse response = bookingService.bookRide(riderId, rideId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/bookings/me")
    @PreAuthorize("hasAnyAuthority('ROLE_RIDER', 'ROLE_BOTH')")
    public ResponseEntity<List<BookingResponse>> getMyBookings(
            @AuthenticationPrincipal Long currentUserId
    ) {
        List<BookingResponse> bookings = bookingService.getBookingsByRider(currentUserId);
        return ResponseEntity.ok(bookings);
    }

    @PatchMapping("/api/bookings/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ROLE_RIDER', 'ROLE_BOTH')")
    public ResponseEntity<BookingResponse> cancelBooking(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal Long currentUserId
    ) {
        BookingResponse response = bookingService.cancel(id, currentUserId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/rides/{id}/bookings")
    @PreAuthorize("hasAnyAuthority('ROLE_DRIVER', 'ROLE_BOTH')")
    public ResponseEntity<List<BookingResponse>> getBookingsForRide(
            @PathVariable("id") Long rideId,
            @AuthenticationPrincipal Long currentUserId
    ) {
        List<BookingResponse> bookings = bookingService.getBookingsForRide(rideId, currentUserId);
        return ResponseEntity.ok(bookings);
    }
}

