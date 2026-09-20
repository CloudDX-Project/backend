package com.travel.trip.plan.edit;

import com.travel.trip.plan.type.TripPlanItemType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "일정에서 교체할 수 있는 장소 검색 결과")
public record PlanPlace(
        @Schema(description = "내부 장소 ID") Long placeId,
        @Schema(description = "장소 종류") TripPlanItemType type,
        @Schema(description = "장소명") String name,
        @Schema(description = "장소 분류") String category,
        @Schema(description = "위도") Double latitude,
        @Schema(description = "경도") Double longitude,
        @Schema(description = "주소") String address
) {}
