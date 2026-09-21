package com.travel.trip.entity;

import com.travel.trip.dto.RentalCandidate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "trip_rentals",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_trip_rental_trip",
                        columnNames = "trip_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripRentalSelection {

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
            name = "rental_id",
            nullable = false,
            length = 100
    )
    private String rentalId;

    @Column(
            nullable = false,
            length = 255
    )
    private String company;

    @Column(length = 255)
    private String car;

    @Column(length = 255)
    private String pickup;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(
            name = "estimated_shuttle_minutes",
            nullable = false
    )
    private Integer estimatedShuttleMinutes;

    @Builder
    public TripRentalSelection(
            Trip trip,
            String rentalId,
            String company,
            String car,
            String pickup,
            Double latitude,
            Double longitude,
            Integer estimatedShuttleMinutes
    ) {
        this.trip = trip;
        this.rentalId = rentalId;
        this.company = company;
        this.car = car;
        this.pickup = pickup;
        this.latitude = latitude;
        this.longitude = longitude;
        this.estimatedShuttleMinutes =
                estimatedShuttleMinutes;
    }

    public static TripRentalSelection from(
            Trip trip,
            RentalCandidate rental
    ) {
        return TripRentalSelection.builder()
                .trip(trip)
                .rentalId(rental.id())
                .company(rental.company())
                .car(rental.car())
                .pickup(rental.pickup())
                .latitude(rental.latitude())
                .longitude(rental.longitude())
                .estimatedShuttleMinutes(
                        rental.estimatedShuttleMinutes()
                )
                .build();
    }
}