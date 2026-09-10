package com.travel.restaurant.dto;

import com.travel.restaurant.data.RestaurantMenuData;
import com.travel.trip.entity.FoodPreference;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record RestaurantCandidate(

        Long id,

        String kakaoPlaceId,

        String restaurantName,

        String category,

        Set<FoodPreference> matchedFoodPreferences,

        String phone,

        String address,

        String roadAddress,

        Double latitude,

        Double longitude,

        String placeUrl,

        Double rating,

        Integer reviewCount,

        String businessHours,

        String summary,

        String tags,

        String facilities,

        LocalDateTime lastScrapedAt,

        Double distanceKm,

        Integer estimatedDriveMinutes,

        List<RestaurantMenuData> menus

) {
}