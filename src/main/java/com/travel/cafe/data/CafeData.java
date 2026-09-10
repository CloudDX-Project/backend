package com.travel.cafe.data;

import java.io.Serializable;
import java.time.LocalDateTime;

public record CafeData(

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

        String lastScrapeStatus,

        LocalDateTime createdAt,

        LocalDateTime updatedAt

) implements Serializable {
}