package com.travel.trip.plan.dto;

import com.travel.trip.entity.SegmentTransportMode;
import com.travel.trip.plan.type.TripPlanItemType;

import java.time.LocalDateTime;

public record TripPlanItemResponse(

        Integer order,

        TripPlanItemType type,

        /*
         * 관광지 / 식당 / 카페 / 숙소의 DB ID.
         * 공항 / 항공 / 출발지는 null.
         */
        Long placeId,

        /*
         * 항공편 ID 또는 공항 코드 등 외부 식별자.
         */
        String referenceId,

        String name,

        String category,

        Double latitude,

        Double longitude,

        /*
         * Routing 연결 전에는
         * 항공편은 실제 시간,
         * 관광지/식당/카페는 Bedrock의 대략적인 시간이다.
         */
        LocalDateTime startAt,

        LocalDateTime endAt,

        Integer stayMinutes,

        /*
         * 이전 item에서 현재 item까지의 이동수단.
         * 실제 거리/시간은 나중에 TransportSegment + Routing으로 계산한다.
         */
        SegmentTransportMode transportModeFromPrevious,

        String reason

) {
}