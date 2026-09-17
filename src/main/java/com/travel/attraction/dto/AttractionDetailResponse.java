package com.travel.attraction.dto;

import com.travel.attraction.data.TouristAttractionData;

public record AttractionDetailResponse(
        Long id, String name, String provider, String providerId,
        String categoryName, String region1Name, String region2Name,
        String address, String roadAddress, String postcode,
        Double latitude, Double longitude, String tags, String allTags,
        String introduction, String phoneNumber,
        String representativeImageUrl, String thumbnailImageUrl
) {
    public static AttractionDetailResponse from(TouristAttractionData data) {
        return new AttractionDetailResponse(
                data.id(), data.name(), data.provider(), data.providerId(),
                data.categoryName(), data.region1Name(), data.region2Name(),
                data.address(), data.roadAddress(), data.postcode(),
                data.latitude(), data.longitude(), data.tags(), data.allTags(),
                data.introduction(), data.phoneNumber(),
                data.representativeImageUrl(), data.thumbnailImageUrl());
    }
}
