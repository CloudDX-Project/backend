package com.travel.restaurant.dto;

import com.travel.trip.entity.FoodPreference;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record RestaurantSearchRequest(

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
                message = "음식 취향은 최대 3개까지 선택할 수 있습니다."
        )
        Set<FoodPreference> foodPreferences,

        @Min(1)
        @Max(100)
        Integer limit

) {

    public Set<FoodPreference> resolvedFoodPreferences() {

        return foodPreferences == null
                ? Set.of()
                : foodPreferences;
    }

    public int resolvedLimit() {

        return limit == null
                ? 30
                : limit;
    }
}