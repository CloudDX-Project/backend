package com.travel.trip.plan.controller;

import com.travel.global.response.ApiResponse;
import com.travel.trip.plan.dto.TripPlanCreateRequest;
import com.travel.trip.plan.dto.TripPlanResponse;
import com.travel.trip.plan.service.TripPlanService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips")
public class TripPlanController {

    private final TripPlanService tripPlanService;

    public TripPlanController(
            TripPlanService tripPlanService
    ) {
        this.tripPlanService =
                tripPlanService;
    }

    @PostMapping("/{tripId}/plan")
    public ApiResponse<TripPlanResponse> createPlan(
            Authentication authentication,
            @PathVariable Long tripId,
            @Valid @RequestBody TripPlanCreateRequest request
    ) {

        Long userId =
                (Long) authentication.getPrincipal();

        return ApiResponse.success(
                "여행 일정이 생성되었습니다.",
                tripPlanService.createPlan(
                        userId,
                        tripId,
                        request
                )
        );
    }
}