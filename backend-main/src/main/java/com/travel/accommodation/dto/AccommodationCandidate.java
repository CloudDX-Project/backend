package com.travel.accommodation.dto;

import com.travel.accommodation.type.AccommodationSource;

/**
 * 숙소 검색 결과.
 *
 * business 계열 필드는 공공데이터(accommodation_business),
 * provider/rating/review 계열 필드는 accommodation_enrichment에서 온다.
 */
public record AccommodationCandidate(

        Long id,

        String businessName,

        String businessType,

        String roadAddress,

        String lotAddress,

        String province,

        String city,

        String town,

        AccommodationSource source,

        Double latitude,

        Double longitude,

        String provider,

        String providerId,

        String providerName,

        String providerUrl,

        String providerAddress,

        Double rating,

        Double ratingScale,

        Integer reviewCount,

        Integer visitorReviewCount,

        Integer blogReviewCount,

        Long price,

        Long priceAvg,

        String priceText,

        String representativeImageUrl,

        String checkInTime,

        String checkOutTime,

        Integer starCount,

        String description,

        String phoneNumber

) {
}
