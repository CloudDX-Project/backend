package com.travel.trip.dto;

public record TripCostResponse(
        long mealCost,
        long transportCost,
        long activityCost,
        long accommodationCost,
        long totalCost,
        long remainingBudget
) {
}