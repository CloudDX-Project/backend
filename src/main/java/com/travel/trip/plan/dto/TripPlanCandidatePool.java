package com.travel.trip.plan.dto;

import com.travel.weather.DailyWeatherResponse;

import java.io.Serializable;
import java.util.List;

public record TripPlanCandidatePool(

        TripPlanSelectedAccommodation accommodation,

        MandatoryDestination mandatoryDestination,

        List<AttractionCandidate> attractions,

        List<RestaurantCandidate> restaurants,

        List<CafeCandidate> cafes,

        List<DailyWeatherResponse> weather

) {

    public record MandatoryDestination(
            Long syntheticId,
            String name,
            Double latitude,
            Double longitude
    ) implements Serializable {
    }

    public record AttractionCandidate(

            Long id,
            String name,
            String category,
            Double latitude,
            Double longitude,
            Double distanceKm,
            Integer estimatedDriveMinutes,
            Double recommendationScore,
            String tags,
            String recommendationReason,
            boolean mandatory,
            Double actualDistanceKmFromAccommodation,
            Integer actualDriveMinutesFromAccommodation,
            Double actualDistanceKmFromDestination,
            Integer actualDriveMinutesFromDestination

    ) implements Serializable {
    }

    public record RestaurantCandidate(

            Long id,
            String name,
            String category,
            Double latitude,
            Double longitude,
            Double distanceKm,
            Integer estimatedDriveMinutes,
            Double recommendationScore,
            List<String> matchedFoodPreferences,
            String summary,
            String tags,
            String recommendationReason,
            Double rating,
            Integer reviewCount,
            String businessHours,
            Double qualityScore,
            Double breakfastFitScore,
            Double lunchFitScore,
            Double dinnerFitScore,
            Double actualDistanceKmFromAccommodation,
            Integer actualDriveMinutesFromAccommodation,
            Double actualDistanceKmFromDestination,
            Integer actualDriveMinutesFromDestination

    ) implements Serializable {
    }

    public record CafeCandidate(

            Long id,
            String name,
            String category,
            Double latitude,
            Double longitude,
            Double distanceKm,
            Integer estimatedDriveMinutes,
            Double recommendationScore,
            String summary,
            String tags,
            String recommendationReason,
            Double rating,
            Integer reviewCount,
            String businessHours,
            Double qualityScore,
            Double actualDistanceKmFromAccommodation,
            Integer actualDriveMinutesFromAccommodation,
            Double actualDistanceKmFromDestination,
            Integer actualDriveMinutesFromDestination

    ) implements Serializable {
    }
}
