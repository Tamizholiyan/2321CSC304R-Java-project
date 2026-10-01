package com.campuscommute.campuscommute.rides;

import com.campuscommute.campuscommute.rides.dto.CreateRideRequest;
import com.campuscommute.campuscommute.rides.dto.RideResponse;
import com.campuscommute.campuscommute.rides.dto.UpdateRideStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rides")
public class RideController {

    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_DRIVER','ROLE_BOTH')")
    public ResponseEntity<RideResponse> createRide(
            @AuthenticationPrincipal Long driverId,
            @Valid @RequestBody CreateRideRequest request
    ) {
        RideResponse response = rideService.createRide(driverId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<RideResponse>> getRides(
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "startLocation", required = false) String startLocation,
            @RequestParam(value = "destination", required = false) String destination
    ) {
        String effectiveStart = (start != null && !start.isBlank()) ? start : startLocation;
        List<RideResponse> rides = rideService.getRides(effectiveStart, destination);
        return ResponseEntity.ok(rides);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RideResponse> getRideById(@PathVariable("id") Long id) {
        RideResponse ride = rideService.getRideById(id);
        return ResponseEntity.ok(ride);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<RideResponse> updateRideStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateRideStatusRequest request
    ) {
        RideResponse updated = rideService.updateRideStatus(id, request.status());
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRide(@PathVariable("id") Long id) {
        rideService.deleteRide(id);
        return ResponseEntity.noContent().build();
    }
}
