package com.travel.restaurant.dto;

import com.travel.trip.entity.FoodPreference;
import com.travel.trip.entity.TripPreference;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record RestaurantRecommendRequest(

        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        Double originLatitude,

        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        Double originLongitude,

        @Size(
                max = 3,
                message = "여행 테마는 최대 3개까지 선택할 수 있습니다."
        )
        Set<TripPreference> preferences,

        @Size(
                max = 3,
                message = "음식 취향은 최대 3개까지 선택할 수 있습니다."
        )
        Set<FoodPreference> foodPreferences,

        @Min(1)
        @Max(30)
        Integer limit

) {

    public Set<TripPreference> resolvedPreferences() {

        return preferences == null
                ? Set.of()
                : preferences;
    }

    public Set<FoodPreference> resolvedFoodPreferences() {

        return foodPreferences == null
                ? Set.of()
                : foodPreferences;
    }

    public boolean foodFocused() {

        return resolvedPreferences()
                .contains(
                        TripPreference.FOOD
                );
    }

    public int resolvedLimit() {

        return limit == null
                ? 10
                : limit;
    }
}