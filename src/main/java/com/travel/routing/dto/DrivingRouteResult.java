package com.travel.routing.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public record DrivingRouteResult(
        long distanceMeters,
        long durationSeconds,
        long taxiFare,
        long tollFare,
        List<RoutePoint> path
) implements Serializable {

    public DrivingRouteResult {
        path = path == null
                ? List.of()
                : new ArrayList<>(path);
    }
}
