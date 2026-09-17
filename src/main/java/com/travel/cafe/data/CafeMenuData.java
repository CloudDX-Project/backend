package com.travel.cafe.data;

import java.time.LocalDateTime;

public record CafeMenuData(

        Long id,

        Long cafeId,

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