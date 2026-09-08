package com.travel.attraction.dto;

import com.travel.trip.entity.TripPace;
import com.travel.trip.entity.TripPreference;
import com.travel.weather.WeatherCondition;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AttractionRecommendRequest(

        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        Double latitude,

        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        Double longitude,

        List<TripPreference> preferences,

        WeatherCondition weatherCondition,

        TripPace pace,

        @Min(1)
        @Max(30)
        Integer limit

) {

    public int resolvedLimit() {
        return limit == null ? 12 : limit;
    }

    public List<TripPreference> resolvedPreferences() {
        return preferences == null
                ? List.of()
                : preferences;
    }

    public WeatherCondition resolvedWeatherCondition() {
        return weatherCondition == null
                ? WeatherCondition.UNKNOWN
                : weatherCondition;
    }

    public TripPace resolvedPace() {
        return pace == null
                ? TripPace.BALANCED
                : pace;
    }
}