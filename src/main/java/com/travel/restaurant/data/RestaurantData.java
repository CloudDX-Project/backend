package com.travel.restaurant.data;

import java.time.LocalDateTime;

public record RestaurantData(

        Long id,

        String kakaoPlaceId,

        String restaurantName,

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

) {
}