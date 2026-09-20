package com.travel.trip.plan.edit;

import com.travel.global.exception.BusinessException;
import com.travel.global.exception.ErrorCode;
import com.travel.global.response.ApiResponse;
import com.travel.trip.plan.type.TripPlanItemType;
import com.travel.trip.repository.TripRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name = "여행 일정 편집", description = "생성된 일정의 장소 변경과 순서 변경을 검증하고 실제 이동시간을 다시 계산해 저장합니다.")
@RestController
@RequestMapping("/api/trips/{tripId}/plan")
@RequiredArgsConstructor
public class PlanEditController {
    private final PlanEditService service;
    private final PlanPlaceService places;
    private final TripRepository trips;

    @GetMapping("/editor")
    @Operation(summary = "일정 편집 정보 조회", description = "현재 일정, 편집 기준 revision과 요청 ID를 조회합니다. 저장 전 최신 상태 확인에 사용합니다.")
    public ApiResponse<PlanEditResponse> get(Authentication auth, @PathVariable Long tripId) {
        return ApiResponse.success(service.get((Long) auth.getPrincipal(), tripId));
    }

    @PutMapping("/editor")
    @Operation(summary = "변경 일정 저장", description = "관광지·음식점·카페의 변경 및 순서를 검증하고 실제 경로와 시간을 재계산해 원자적으로 저장합니다.")
    public ApiResponse<PlanEditResponse> save(Authentication auth, @PathVariable Long tripId,
                                             @Valid @RequestBody PlanEditRequest request) {
        return ApiResponse.success(service.save((Long) auth.getPrincipal(), tripId, request));
    }

    @GetMapping("/places")
    @Operation(summary = "교체 장소 검색", description = "일정에서 교체할 관광지·음식점·카페를 내부 데이터베이스에서 이름으로 검색합니다.")
    public ApiResponse<List<PlanPlace>> search(Authentication auth, @PathVariable Long tripId,
                                               @RequestParam TripPlanItemType type, @RequestParam String query) {
        trips.findByIdAndUserId(tripId, (Long) auth.getPrincipal())
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        return ApiResponse.success(places.search(type, query));
    }
}
