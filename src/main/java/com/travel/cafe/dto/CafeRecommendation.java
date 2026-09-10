package com.travel.cafe.dto;

import java.util.List;

public record CafeRecommendation(

        Long id,

        String kakaoPlaceId,

        String cafeName,

        String category,

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

        List<CafeMenuResponse> menus,

        /*
         * 추천 지표
         */
        Double distanceKm,

        Integer estimatedDriveMinutes,

        Double bayesianRating,

        Double ratingScore,

        Double distanceScore,

        Double experienceScore,

        Double baseScore,

        Double aiScore,

        Double recommendationScore,

        String recommendationReason

) {
}