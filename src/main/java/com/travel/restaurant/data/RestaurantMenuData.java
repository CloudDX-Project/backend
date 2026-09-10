package com.travel.restaurant.data;

import java.time.LocalDateTime;

public record RestaurantMenuData(

        Long id,

        Long restaurantId,

        String menuName,

        String description,

        String imageUrl,

        Integer currentPrice,

        boolean recommended,

        String menuTags,

        LocalDateTime lastSeenAt,

        LocalDateTime createdAt,

        LocalDateTime updatedAt

) {
}