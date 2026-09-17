package com.travel.trip.plan.controller;

import com.travel.global.response.ApiResponse;
import com.travel.trip.plan.async.TripPlanRequestService;
import com.travel.trip.plan.dto.TripPlanRequestResponse;
import com.travel.trip.plan.dto.TripPlanStatusResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
