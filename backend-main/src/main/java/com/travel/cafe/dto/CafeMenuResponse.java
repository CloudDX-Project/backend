package com.travel.cafe.dto;

public record CafeMenuResponse(

        Long id,

        String menuName,

        String description,

        String imageUrl,

        Integer currentPrice,

        boolean recommended,

        String menuTags

) {
}