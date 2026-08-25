package com.travel.trip.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Table(
        name = "trip_days",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_trip_days_trip_day_number",
                        columnNames = {
                                "trip_id",
                                "day_number"
                        }
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(
            name = "day_number",
            nullable = false
    )
    private Integer dayNumber;

    @Column(nullable = false)
    private LocalDate date;

    @OneToMany(
            mappedBy = "tripDay",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("sequence ASC")
    private List<TransportSegment> transportSegments
            = new ArrayList<>();

    @Builder
    public TripDay(
            Trip trip,
            Integer dayNumber,
            LocalDate date
    ) {
        this.trip = trip;
        this.dayNumber = dayNumber;
        this.date = date;
    }

    public void addTransportSegment(
            TransportSegment segment
    ) {
        this.transportSegments.add(segment);
    }

    public void removeTransportSegment(
            TransportSegment segment
    ) {
        this.transportSegments.remove(segment);
    }
}