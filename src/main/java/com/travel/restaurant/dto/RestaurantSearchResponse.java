package com.travel.restaurant.dto;

import com.travel.trip.entity.FoodPreference;

import java.util.List;
import java.util.Set;

public record RestaurantSearchResponse(

        Double originLatitude,

        Double originLongitude,

        Set<FoodPreference> foodPreferences,

        int count,

        String travelTimeBasis,

        List<RestaurantCandidate> restaurants

) {
}