package com.travel.accommodation.data;

import java.time.LocalDateTime;

/**
 * accommodation_enrichment 조회 결과.
 *
 * 이 테이블은 Python 수집기가 직접 관리하기 때문에
 * JPA Entity로 만들지 않고 JDBC 조회용 record로 사용한다.
 *
 * Hibernate ddl-auto=update가
 * accommodation_enrichment 스키마를 건드리지 않도록 하기 위함이다.
 */
public record AccommodationEnrichmentData(

        Long accommodationId,

        String province,

        String city,

        String town,

        String provider,

        String providerId,

        String providerName,

        String providerUrl,

        Double rating,

        Double ratingScale,

        Integer reviewCount,

        Integer visitorReviewCount,

        Integer blogReviewCount,

        Long price,

        Long priceAvg,

        String priceText,

        String providerAddress,

        String representativeImageUrl,

        Double providerLatitude,

        Double providerLongitude,

        String checkInTime,

        String checkOutTime,

        Integer starCount,

        String description,

        String phoneNumber,

        String scrapeStatus,

        LocalDateTime collectedAt,

        LocalDateTime updatedAt

) {
}