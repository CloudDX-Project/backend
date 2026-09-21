package com.travel.trip.plan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.travel.trip.plan.async.TripPlanGenerationStatus;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TripPlanStatusResponse(
        Long tripId,
        String requestId,
        TripPlanGenerationStatus status,
        TripPlanResponse plan,
        String errorMessage,
        LocalDateTime requestedAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
}
