package com.travel.trip.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "trip_accommodations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_trip_accommodation_trip",
                        columnNames = "trip_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripAccommodationSelection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "trip_id",
            nullable = false,
            unique = true
    )
    private Trip trip;

    @Column(
            name = "accommodation_id",
            nullable = false
    )
    private Long accommodationId;

    @Column(
            name = "provider_id",
            length = 100
    )
    private String providerId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 500)
    private String address;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "check_in_time", length = 20)
    private String checkInTime;

    @Column(name = "check_out_time", length = 20)
    private String checkOutTime;

    @Builder
    public TripAccommodationSelection(
            Trip trip,
            Long accommodationId,
            String providerId,
            String name,
            String address,
            Double latitude,
            Double longitude,
            String checkInTime,
            String checkOutTime
    ) {
        this.trip = trip;
        this.accommodationId = accommodationId;
        this.providerId = providerId;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.checkInTime = checkInTime;
        this.checkOutTime = checkOutTime;
    }
}