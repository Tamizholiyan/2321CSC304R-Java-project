package com.campuscommute.campuscommute.rides;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "rides")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ride {

    public static final String EEC_NAME = "Easwari Engineering College, Ramapuram";
    public static final double EEC_LAT = 13.0315;
    public static final double EEC_LNG = 80.1804;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ride_id")
    private Long rideId;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(name = "start_location", nullable = false)
    private String startLocation;

    @Column(name = "destination", nullable = false)
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false)
    private VehicleType vehicleType;

    @Column(name = "vehicle_model", length = 100)
    private String vehicleModel;

    @Column(name = "vehicle_plate", length = 20)
    private String vehiclePlate;

    @Column(name = "departure_time")
    private LocalDateTime departureTime;

    @Column(name = "pickup_lat")
    private Double pickupLat;

    @Column(name = "pickup_lng")
    private Double pickupLng;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "mileage_kmpl", precision = 10, scale = 2, nullable = false)
    private BigDecimal mileageKmpl;

    @Column(name = "total_distance_km", precision = 10, scale = 2, nullable = false)
    private BigDecimal totalDistanceKm;

    @Column(name = "available_seats", nullable = false)
    private Integer availableSeats;

    @Enumerated(EnumType.STRING)
    @Column(name = "ride_status", nullable = false)
    private RideStatus rideStatus;

    @Column(name = "fuel_rate_per_litre", precision = 10, scale = 2)
    private BigDecimal fuelRatePerLitre;
}
