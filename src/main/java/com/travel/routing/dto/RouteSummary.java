package com.travel.routing.dto;

import java.io.Serializable;

public record RouteSummary(
        String key,
        Double distanceKm,
        Integer durationMinutes
) implements Serializable {
}
