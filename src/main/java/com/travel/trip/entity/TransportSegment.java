package com.travel.trip.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "transport_segments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_transport_segment_day_sequence",
                        columnNames = {
                                "trip_day_id",
                                "segment_order"
                        }
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransportSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "trip_day_id",
            nullable = false
    )
    private TripDay tripDay;

    @Column(
            name = "segment_order",
            nullable = false
    )
    private Integer sequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SegmentTransportMode mode;

    @Column(nullable = false, length = 100)
    private String departureName;

    @Column(nullable = false, length = 100)
    private String arrivalName;

    private Double departureLatitude;

    private Double departureLongitude;

    private Double arrivalLatitude;

    private Double arrivalLongitude;

    private LocalDateTime departureAt;

    private LocalDateTime arrivalAt;

    private Double distanceKm;

    private Long durationMinutes;

    @Column(nullable = false)
    private Long cost;

    @Column(length = 30)
    private String routeProvider;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String routePathJson;

    @Builder
    public TransportSegment(
            TripDay tripDay,
            Integer sequence,
            SegmentTransportMode mode,
            String departureName,
            String arrivalName,
            Double departureLatitude,
            Double departureLongitude,
            Double arrivalLatitude,
            Double arrivalLongitude,
            LocalDateTime departureAt,
            LocalDateTime arrivalAt,
            Double distanceKm,
            Long durationMinutes,
            Long cost,
            String routeProvider,
            String routePathJson
    ) {
        this.tripDay = tripDay;
        this.sequence = sequence;
        this.mode = mode;
        this.departureName = departureName;
        this.arrivalName = arrivalName;
        this.departureLatitude = departureLatitude;
        this.departureLongitude = departureLongitude;
        this.arrivalLatitude = arrivalLatitude;
        this.arrivalLongitude = arrivalLongitude;
        this.departureAt = departureAt;
        this.arrivalAt = arrivalAt;
        this.distanceKm = distanceKm;
        this.durationMinutes = durationMinutes;
        this.cost = cost == null ? 0L : cost;
        this.routeProvider = routeProvider;
        this.routePathJson = routePathJson;
    }

    public void updateDetails(
            SegmentTransportMode mode,
            String departureName,
            String arrivalName,
            Double departureLatitude,
            Double departureLongitude,
            Double arrivalLatitude,
            Double arrivalLongitude,
            LocalDateTime departureAt,
            LocalDateTime arrivalAt
    ) {
        this.mode = mode;

        this.departureName = departureName;
        this.arrivalName = arrivalName;

        this.departureLatitude = departureLatitude;
        this.departureLongitude = departureLongitude;

        this.arrivalLatitude = arrivalLatitude;
        this.arrivalLongitude = arrivalLongitude;

        this.departureAt = departureAt;
        this.arrivalAt = arrivalAt;

        /*
         * 출발/도착 좌표나 교통수단이 변경되면
         * 기존 경로 계산 결과는 더 이상 유효하지 않다.
         */
        this.distanceKm = null;
        this.durationMinutes = null;
        this.cost = 0L;
        this.routeProvider = null;
        this.routePathJson = null;
    }

    public void changeSequence(
            Integer sequence
    ) {
        this.sequence = sequence;
    }


    public void updateRouteResult(
            Double distanceKm,
            Long durationMinutes,
            Long cost,
            LocalDateTime departureAt,
            LocalDateTime arrivalAt,
            String routeProvider,
            String routePathJson
    ) {
        this.distanceKm = distanceKm;
        this.durationMinutes = durationMinutes;
        this.cost = cost == null ? 0L : cost;
        this.departureAt = departureAt;
        this.arrivalAt = arrivalAt;
        this.routeProvider = routeProvider;
        this.routePathJson = routePathJson;
    }
}