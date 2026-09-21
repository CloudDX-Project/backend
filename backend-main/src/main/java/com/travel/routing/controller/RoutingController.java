package com.travel.routing.controller;

import com.travel.global.response.ApiResponse;
import com.travel.routing.dto.DrivingRouteRequest;
import com.travel.routing.dto.DrivingRouteResponse;
import com.travel.routing.service.RoutingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "이동 경로", description = "두 장소의 좌표를 기준으로 자동차 이동 거리, 시간 및 지도 경로를 계산합니다.")
@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RoutingController {

    private final RoutingService routingService;

    @PostMapping("/driving")
    @Operation(summary = "자동차 경로 계산", description = "출발·도착 좌표를 Kakao Mobility 길찾기에 전달해 거리, 소요시간, 통행료와 경로 좌표를 반환합니다. 동일 위치는 외부 호출 없이 처리합니다.")
    public ApiResponse<DrivingRouteResponse> driving(
            @Valid
            @RequestBody
            DrivingRouteRequest request
    ) {
        return ApiResponse.success(
                DrivingRouteResponse.from(
                        routingService.findDrivingRoute(
                                request.originLatitude(),
                                request.originLongitude(),
                                request.destinationLatitude(),
                                request.destinationLongitude()
                        )
                )
        );
    }
}
