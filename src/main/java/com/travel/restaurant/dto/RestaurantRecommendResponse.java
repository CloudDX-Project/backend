package com.travel.restaurant.dto;

import com.travel.trip.entity.FoodPreference;

import java.util.List;
import java.util.Set;

public record RestaurantRecommendResponse(

        Double originLatitude,

        Double originLongitude,

        boolean foodFocused,

        Set<FoodPreference> foodPreferences,

        int requestedLimit,

        int searchWindowMinutes,

        String travelTimeBasis,

        int candidatePoolCount,

        List<RestaurantRecommendation> restaurants

) {
}