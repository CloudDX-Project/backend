package com.travel.attraction.dto;

public record AttractionRecommendation(

        Long id,

        String provider,
        String providerId,

        String name,

        String categoryCode,
        String categoryName,

        String region1Code,
        String region1Name,

        String region2Code,
        String region2Name,

        String address,
        String roadAddress,
        String postcode,

        Double latitude,
        Double longitude,

        String tags,
        String allTags,

        String introduction,
        String phoneNumber,

        String photoId,
        String representativeImageUrl,
        String thumbnailImageUrl,

        // 추천 전용

        Double distanceKm,

        Integer estimatedDriveMinutes,

        Double preferenceScore,

        Double weatherScore,

        Double paceScore,

        Double baseScore,

        Double aiScore,

        Double recommendationScore,

        String recommendationReason

) {
}