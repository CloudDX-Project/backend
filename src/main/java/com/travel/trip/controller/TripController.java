package com.travel.trip.controller;

import com.travel.global.response.ApiResponse;
import com.travel.trip.dto.TripCreateRequest;
import com.travel.trip.dto.TripResponse;
import com.travel.trip.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping
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
    public ApiResponse<List<TripResponse>> getMyTrips(
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();

        return ApiResponse.success(
                tripService.getMyTrips(userId)
        );
    }
}