package com.travel.trip.controller;

import com.travel.global.response.ApiResponse;
import com.travel.trip.dto.TripCostResponse;
import com.travel.trip.dto.TripCreateRequest;
import com.travel.trip.dto.TripResponse;
import com.travel.trip.service.TripCostService;
import com.travel.trip.service.TripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "여행", description = "로그인 사용자의 여행을 생성·조회하고 전체 예상 비용을 확인합니다.")
@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;
    private final TripCostService tripCostService;

    @PostMapping
    @Operation(summary = "여행 생성", description = "출발지, 목적지, 날짜, 인원, 교통수단과 선택 상품을 저장하고 여행 ID를 발급합니다.")
    public ApiResponse<TripResponse> createTrip(
            Authentication authentication,
            @Valid @RequestBody TripCreateRequest request
    ) {
        Long userId = (Long) authentication.getPrincipal();

        return ApiResponse.success(
                "여행이 생성되었습니다.",
                tripService.createTrip(userId, request)
        );
    }

    @GetMapping("/{tripId}")
    @Operation(summary = "여행 상세 조회", description = "본인이 생성한 여행의 입력 조건, 선택 항공·숙소·렌터카 및 저장된 일정을 조회합니다.")
    public ApiResponse<TripResponse> getTrip(
            Authentication authentication,
            @PathVariable Long tripId
    ) {
        Long userId = (Long) authentication.getPrincipal();

        return ApiResponse.success(
                tripService.getTrip(userId, tripId)
        );
    }

    @GetMapping
    @Operation(summary = "내 여행 목록 조회", description = "현재 로그인 사용자가 생성한 여행 목록을 최신순으로 조회합니다.")
    public ApiResponse<List<TripResponse>> getMyTrips(
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();

        return ApiResponse.success(
                tripService.getMyTrips(userId)
        );
    }
    @GetMapping("/{tripId}/cost")
    @Operation(summary = "여행 비용 조회", description = "저장된 교통, 숙소, 렌터카와 일정 항목을 기준으로 여행의 예상 비용을 조회합니다.")
    public ApiResponse<TripCostResponse> getTripCost(
            Authentication authentication,
            @PathVariable Long tripId
    ) {
        Long userId = (Long) authentication.getPrincipal();

        return ApiResponse.success(
                tripCostService.getTripCost(userId, tripId)
        );
    }
}
