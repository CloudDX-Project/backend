package com.travel.trip.plan.async;

public record TripPlanQueueMessage(
        String requestId,
        Long userId,
        Long tripId
) {
}
