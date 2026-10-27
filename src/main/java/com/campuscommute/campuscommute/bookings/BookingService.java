package com.campuscommute.campuscommute.bookings;

import com.campuscommute.campuscommute.bookings.dto.BookingResponse;
import com.campuscommute.campuscommute.rides.InvalidRideStateException;
import com.campuscommute.campuscommute.rides.Ride;
import com.campuscommute.campuscommute.rides.RideNotFoundException;
import com.campuscommute.campuscommute.rides.RideRepository;
import com.campuscommute.campuscommute.rides.RideStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RideRepository rideRepository;

    public BookingService(BookingRepository bookingRepository, RideRepository rideRepository) {
        this.bookingRepository = bookingRepository;
        this.rideRepository = rideRepository;
    }

    @Transactional
    public BookingResponse bookRide(Long riderId, Long rideId) {
        // Pessimistic write lock ensures row-level isolation on the ride
        Ride ride = rideRepository.findByIdForUpdate(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));

        if (ride.getRideStatus() == RideStatus.FULL || ride.getAvailableSeats() <= 0) {
            throw new OverbookingException("No available seats left on this ride");
        }

        if (ride.getRideStatus() != RideStatus.SCHEDULED) {
            throw new InvalidRideStateException("Cannot book a ride in status: " + ride.getRideStatus());
        }

        if (bookingRepository.existsByRideIdAndRiderIdAndBookingStatus(rideId, riderId, BookingStatus.CONFIRMED)) {
            throw new DuplicateBookingException("Rider already has a confirmed booking for this ride");
        }

        if (ride.getAvailableSeats() <= 0) {
            throw new OverbookingException("No available seats left on this ride");
        }

        // Atomically decrement available seats and flip status to FULL when seats reach 0
        int remainingSeats = ride.getAvailableSeats() - 1;
        ride.setAvailableSeats(remainingSeats);
        if (remainingSeats == 0) {
            ride.setRideStatus(RideStatus.FULL);
        }
        rideRepository.save(ride);

        Booking booking = Booking.builder()
                .rideId(rideId)
                .riderId(riderId)
                .bookingTime(LocalDateTime.now())
                .bookingStatus(BookingStatus.CONFIRMED)
                .build();
        Booking savedBooking = bookingRepository.save(booking);

        return BookingResponse.from(savedBooking);
    }

    @Transactional
    public BookingResponse cancel(Long bookingId, Long riderId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (!booking.getRiderId().equals(riderId)) {
            throw new AccessDeniedException("You do not have permission to cancel this booking");
        }

        if (booking.getBookingStatus() == BookingStatus.CANCELLED) {
            return BookingResponse.from(booking);
        }

        Ride ride = rideRepository.findByIdForUpdate(booking.getRideId())
                .orElseThrow(() -> new RideNotFoundException(booking.getRideId()));

        if (ride.getRideStatus() == RideStatus.ONGOING || ride.getRideStatus() == RideStatus.COMPLETED) {
            throw new InvalidRideStateException("Cannot cancel booking for a ride that is " + ride.getRideStatus());
        }

        booking.setBookingStatus(BookingStatus.CANCELLED);
        Booking savedBooking = bookingRepository.save(booking);

        if (ride.getRideStatus() == RideStatus.FULL) {
            ride.setRideStatus(RideStatus.SCHEDULED);
        }
        ride.setAvailableSeats(ride.getAvailableSeats() + 1);
        rideRepository.save(ride);

        return BookingResponse.from(savedBooking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsByRider(Long riderId) {
        return bookingRepository.findByRiderId(riderId).stream()
                .map(BookingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsForRide(Long rideId, Long currentUserId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));

        if (currentUserId != null && !ride.getDriverId().equals(currentUserId)) {
            throw new AccessDeniedException("Only the driver of this ride can view its bookings");
        }

        return bookingRepository.findByRideId(rideId).stream()
                .map(BookingResponse::from)
                .toList();
    }
}

