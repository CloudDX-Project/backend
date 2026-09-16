package com.travel.routing.controller;

import com.travel.global.response.ApiResponse;
import com.travel.routing.dto.DrivingRouteRequest;
import com.travel.routing.dto.DrivingRouteResponse;
import com.travel.routing.service.RoutingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RoutingController {

    private final RoutingService routingService;

    @PostMapping("/driving")
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
