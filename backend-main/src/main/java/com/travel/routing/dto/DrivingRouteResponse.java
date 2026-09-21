package com.travel.routing.dto;

import java.util.List;

public record DrivingRouteResponse(
        Double distanceKm,
        Long durationMinutes,
        Long taxiFare,
        Long tollFare,
        String routeProvider,
        List<RoutePoint> path
) {

    public static DrivingRouteResponse from(
            DrivingRouteResult result
    ) {
        return new DrivingRouteResponse(
                Math.round(result.distanceMeters() / 100.0) / 10.0,
                Math.max(1L, (long) Math.ceil(result.durationSeconds() / 60.0)),
                result.taxiFare(),
                result.tollFare(),
                "KAKAO_MOBILITY",
                result.path()
        );
    }
}
