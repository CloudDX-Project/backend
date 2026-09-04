package com.travel.accommodation.data;

import java.time.LocalDateTime;

/**
 * accommodation_enrichment 조회 결과.
 *
 * JPA Entity로 매핑하지 않는 이유는, 이 테이블은 별도 수집기가 관리하므로
 * Hibernate ddl-auto=update가 테이블 스키마를 변경하지 못하게 하기 위함이다.
 */
public record AccommodationEnrichmentData(
        Long accommodationId,
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
