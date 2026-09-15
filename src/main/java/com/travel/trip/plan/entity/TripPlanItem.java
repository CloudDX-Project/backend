package com.travel.trip.plan.entity;

import com.travel.trip.entity.SegmentTransportMode;
import com.travel.trip.entity.TripDay;
import com.travel.trip.plan.dto.TripPlanItemResponse;
import com.travel.trip.plan.type.TripPlanItemType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "trip_plan_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_trip_plan_item_day_order",
                        columnNames = {
                                "trip_day_id",
                                "item_order"
                        }
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripPlanItem {

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
            name = "item_order",
            nullable = false
    )
    private Integer itemOrder;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "item_type",
            nullable = false,
            length = 30
    )
    private TripPlanItemType type;

    @Column(name = "place_id")
    private Long placeId;

    @Column(name = "reference_id", length = 255)
    private String referenceId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 255)
    private String category;

    private Double latitude;

    private Double longitude;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "stay_minutes")
    private Integer stayMinutes;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "transport_mode_from_previous",
            length = 30
    )
    private SegmentTransportMode transportModeFromPrevious;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Builder
    public TripPlanItem(
            TripDay tripDay,
            Integer itemOrder,
            TripPlanItemType type,
            Long placeId,
            String referenceId,
            String name,
            String category,
            Double latitude,
            Double longitude,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Integer stayMinutes,
            SegmentTransportMode transportModeFromPrevious,
            String reason
    ) {
        this.tripDay = tripDay;
        this.itemOrder = itemOrder;
        this.type = type;
        this.placeId = placeId;
        this.referenceId = referenceId;
        this.name = name;
        this.category = category;
        this.latitude = latitude;
        this.longitude = longitude;
        this.startAt = startAt;
        this.endAt = endAt;
        this.stayMinutes = stayMinutes;
        this.transportModeFromPrevious = transportModeFromPrevious;
        this.reason = reason;
    }

    public static TripPlanItem from(
            TripDay tripDay,
            TripPlanItemResponse response
    ) {
        return TripPlanItem.builder()
                .tripDay(tripDay)
                .itemOrder(response.order())
                .type(response.type())
                .placeId(response.placeId())
                .referenceId(response.referenceId())
                .name(response.name())
                .category(response.category())
                .latitude(response.latitude())
                .longitude(response.longitude())
                .startAt(response.startAt())
                .endAt(response.endAt())
                .stayMinutes(response.stayMinutes())
                .transportModeFromPrevious(
                        response.transportModeFromPrevious()
                )
                .reason(response.reason())
                .build();
    }
}