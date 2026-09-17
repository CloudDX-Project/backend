package com.travel.restaurant.dto;

import com.travel.restaurant.data.RestaurantMenuData;
import com.travel.trip.entity.FoodPreference;

import java.util.List;
import java.util.Set;

public record RestaurantRecommendation(

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

        List<RestaurantMenuData> menus,

        /*
         * 추천 계산 정보
         */
        Double distanceKm,

        Integer estimatedDriveMinutes,

        Double bayesianRating,

        Double ratingScore,

        Double distanceScore,

        Double localScore,

        Double baseScore,

        Double aiScore,

        Double recommendationScore,

        String recommendationReason

) {
}