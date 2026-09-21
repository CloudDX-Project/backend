package com.travel.trip.plan.dto;

import com.travel.trip.entity.SegmentTransportMode;
import com.travel.trip.plan.entity.TripPlanItem;
import com.travel.trip.plan.type.TripPlanItemType;

import java.time.LocalDateTime;

public record TripPlanItemResponse(

        Integer order,

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

    public static TripPlanItemResponse from(
            TripPlanItem item
    ) {
        return new TripPlanItemResponse(
                item.getItemOrder(),
                item.getType(),
                item.getPlaceId(),
                item.getReferenceId(),
                item.getName(),
                item.getCategory(),
                item.getLatitude(),
                item.getLongitude(),
                item.getStartAt(),
                item.getEndAt(),
                item.getStayMinutes(),
                item.getTransportModeFromPrevious(),
                item.getReason()
        );
    }
}