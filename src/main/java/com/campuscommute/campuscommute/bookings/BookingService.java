package com.campuscommute.campuscommute.bookings;

import com.campuscommute.campuscommute.auth.StudentDirectory;
import com.campuscommute.campuscommute.auth.StudentDirectoryRepository;
import com.campuscommute.campuscommute.auth.User;
import com.campuscommute.campuscommute.auth.UserRepository;
import com.campuscommute.campuscommute.bookings.dto.BookingResponse;
import com.campuscommute.campuscommute.rides.InvalidRideStateException;
import com.campuscommute.campuscommute.rides.Ride;
import com.campuscommute.campuscommute.rides.RideNotFoundException;
import com.campuscommute.campuscommute.rides.RideRepository;
import com.campuscommute.campuscommute.rides.RideStatus;
import com.campuscommute.campuscommute.rides.ScheduleConflictService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RideRepository rideRepository;
    private final UserRepository userRepository;
    private final StudentDirectoryRepository studentDirectoryRepository;
    private final ScheduleConflictService scheduleConflictService;

    public BookingService(
            BookingRepository bookingRepository,
            RideRepository rideRepository,
            UserRepository userRepository,
            StudentDirectoryRepository studentDirectoryRepository,
            ScheduleConflictService scheduleConflictService
    ) {
        this.bookingRepository = bookingRepository;
        this.rideRepository = rideRepository;
        this.userRepository = userRepository;
        this.studentDirectoryRepository = studentDirectoryRepository;
        this.scheduleConflictService = scheduleConflictService;
    }

    @Transactional
    public BookingResponse bookRide(Long riderId, Long rideId) {
        // Race safety: Fixed lock order - lock User FIRST, then lock Ride SECOND.
        // Booking acquires User -> Ride. Ride creation acquires User. Cancel acquires Ride.
        // Acquiring locks in this strictly consistent hierarchy (User, then Ride) prevents deadlocks
        // between concurrent ride creation and booking operations.
        userRepository.findByIdForUpdate(riderId)
                .orElseThrow(() -> new IllegalArgumentException("Rider not found with ID: " + riderId));

        // Pessimistic write lock ensures row-level isolation on the ride
        Ride ride = rideRepository.findByIdForUpdate(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));

        // A user cannot book their own ride (409 with clear message)
        if (ride.getDriverId().equals(riderId)) {
            throw new InvalidRideStateException("Drivers cannot book a seat on their own ride");
        }

        if (bookingRepository.existsByRideIdAndRiderIdAndBookingStatus(rideId, riderId, BookingStatus.CONFIRMED)) {
            throw new DuplicateBookingException("Rider already has a confirmed booking for this ride");
        }

        if (ride.getRideStatus() == RideStatus.FULL || ride.getAvailableSeats() <= 0) {
            throw new OverbookingException("No available seats left on this ride");
        }

        if (ride.getRideStatus() != RideStatus.SCHEDULED) {
            throw new InvalidRideStateException("Cannot book a ride in status: " + ride.getRideStatus());
        }

        // Decision D7: Unified schedule conflict check
        scheduleConflictService.assertFree(riderId, ride.getDepartureTime());

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

        return toBookingResponse(savedBooking, ride);
    }

    @Transactional
    public BookingResponse cancel(Long bookingId, Long riderId) {
        Booking existingBooking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (!existingBooking.getRiderId().equals(riderId)) {
            throw new AccessDeniedException("You do not have permission to cancel this booking");
        }

        // Lock the ride FIRST using findByIdForUpdate
        Ride ride = rideRepository.findByIdForUpdate(existingBooking.getRideId())
                .orElseThrow(() -> new RideNotFoundException(existingBooking.getRideId()));

        // THEN re-read the booking status so two simultaneous cancels cannot add the seat twice
        Booking refreshedBooking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (refreshedBooking.getBookingStatus() == BookingStatus.CANCELLED) {
            return toBookingResponse(refreshedBooking, ride);
        }

        // Do not add a seat back if the ride is CANCELLED/ONGOING/COMPLETED
        if (ride.getRideStatus() == RideStatus.ONGOING || ride.getRideStatus() == RideStatus.COMPLETED) {
            throw new InvalidRideStateException("Cannot cancel booking for a ride that is " + ride.getRideStatus());
        }

        refreshedBooking.setBookingStatus(BookingStatus.CANCELLED);
        Booking savedBooking = bookingRepository.save(refreshedBooking);

        // Only restore available seat if the ride is SCHEDULED or FULL
        if (ride.getRideStatus() == RideStatus.SCHEDULED || ride.getRideStatus() == RideStatus.FULL) {
            if (ride.getRideStatus() == RideStatus.FULL) {
                ride.setRideStatus(RideStatus.SCHEDULED);
            }
            ride.setAvailableSeats(ride.getAvailableSeats() + 1);
            rideRepository.save(ride);
        }

        return toBookingResponse(savedBooking, ride);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsByRider(Long riderId) {
        return bookingRepository.findByRiderId(riderId).stream()
                .map(b -> toBookingResponse(b, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsForRide(Long rideId, Long currentUserId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));

        if (currentUserId != null && !ride.getDriverId().equals(currentUserId)) {
            throw new AccessDeniedException("Only the driver of this ride can view its bookings");
        }

        // Returns confirmed bookings only for driver inspection
        return bookingRepository.findByRideIdAndBookingStatus(rideId, BookingStatus.CONFIRMED).stream()
                .map(b -> toBookingResponse(b, ride))
                .toList();
    }

    private BookingResponse toBookingResponse(Booking booking, Ride ride) {
        if (ride == null && booking.getRideId() != null) {
            ride = rideRepository.findById(booking.getRideId()).orElse(null);
        }

        String driverName = null;
        String driverPhone = null;
        if (ride != null && ride.getDriverId() != null) {
            User driver = userRepository.findById(ride.getDriverId()).orElse(null);
            if (driver != null) {
                driverPhone = driver.getPhoneNumber();
                StudentDirectory s = studentDirectoryRepository.findById(driver.getRegisterNumber()).orElse(null);
                if (s != null) driverName = s.getFullName();
            }
        }

        String riderName = null;
        String riderDepartment = null;
        String riderPhone = null;
        if (booking.getRiderId() != null) {
            User rider = userRepository.findById(booking.getRiderId()).orElse(null);
            if (rider != null) {
                riderPhone = rider.getPhoneNumber();
                StudentDirectory s = studentDirectoryRepository.findById(rider.getRegisterNumber()).orElse(null);
                if (s != null) {
                    riderName = s.getFullName();
                    riderDepartment = s.getDepartment();
                }
            }
        }

        return BookingResponse.of(booking, ride, driverName, driverPhone, riderName, riderDepartment, riderPhone);
    }
}
