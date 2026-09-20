package com.travel.trip.plan.edit;

import com.travel.trip.plan.dto.TripPlanResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/** revision은 프론트 카운터가 아니라 DB에 저장된 일정 생성 엔티티의 @Version 값이다. */
@Schema(description = "일정 편집 조회·저장 결과")
public record PlanEditResponse(
        @Schema(description = "여행 ID", example = "31") Long tripId,
        @Schema(description = "일정 생성 요청 ID") String requestId,
        @Schema(description = "저장된 최신 일정 revision", example = "4") Long revision,
        @Schema(description = "경로와 시간이 다시 계산된 전체 여행 일정") TripPlanResponse plan
) {}
