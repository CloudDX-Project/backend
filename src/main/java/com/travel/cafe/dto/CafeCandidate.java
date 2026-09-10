package com.travel.cafe.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CafeCandidate(

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

        LocalDateTime lastScrapedAt,

        Double distanceKm,

        Integer estimatedDriveMinutes,

        List<CafeMenuResponse> menus

) {
}