package com.travel.trip.plan.controller;

import com.travel.global.response.ApiResponse;
import com.travel.trip.plan.async.TripPlanRequestService;
import com.travel.trip.plan.dto.TripPlanRequestResponse;
import com.travel.trip.plan.dto.TripPlanStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 여행 일정", description = "SQS Worker에 AI 일정 생성을 요청하고 비동기 처리 상태와 완성된 일정을 조회합니다.")
@RestController
@RequestMapping("/api/trips")
public class TripPlanController {

    private final TripPlanRequestService tripPlanRequestService;

    public TripPlanController(
            TripPlanRequestService tripPlanRequestService
    ) {
        this.tripPlanRequestService = tripPlanRequestService;
    }

    @PostMapping("/{tripId}/plan")
    @Operation(summary = "AI 일정 생성 요청", description = "여행 정보를 검증한 뒤 SQS에 일정 생성 작업을 등록합니다. 즉시 202 응답을 반환하며 실제 생성은 Worker가 처리합니다.")
    public ResponseEntity<ApiResponse<TripPlanRequestResponse>> createPlan(
            Authentication authentication,
            @PathVariable Long tripId
    ) {
        Long userId =
                (Long) authentication.getPrincipal();

        TripPlanRequestResponse response = tripPlanRequestService.requestPlan(
                userId,
                tripId
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                ApiResponse.success(
                        "여행 일정 생성 요청이 접수되었습니다.",
                        response
                )
        );
    }

    @GetMapping("/{tripId}/plan")
    @Operation(summary = "AI 일정 생성 상태 조회", description = "일정 생성의 PENDING·PROCESSING·COMPLETED·FAILED 상태를 조회합니다. 완료 시 생성된 일정을 함께 반환합니다.")
    public ApiResponse<TripPlanStatusResponse> getPlanStatus(
            Authentication authentication,
            @PathVariable Long tripId
    ) {
        Long userId = (Long) authentication.getPrincipal();

        return ApiResponse.success(
                tripPlanRequestService.getStatus(userId, tripId)
        );
    }
}
