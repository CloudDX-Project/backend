package com.travel.trip.plan.dto;

import com.travel.trip.plan.async.TripPlanGenerationStatus;

public record TripPlanRequestResponse(
        Long tripId,
        String requestId,
        TripPlanGenerationStatus status
) {
}
