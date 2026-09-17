package com.travel.attraction.dto;

public record AttractionCandidate(

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
        String thumbnailImageUrl

) {
}