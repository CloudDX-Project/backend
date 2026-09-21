package com.travel.restaurant.dto;

import com.travel.restaurant.data.RestaurantMenuData;

public record RestaurantDetailMenuResponse(

        String id,

        String name,

        Integer price,

        String description,

        String imageUrl,

        Boolean isSignature

) {

    public static RestaurantDetailMenuResponse from(
            RestaurantMenuData menu
    ) {
        if (menu == null) {
            return null;
        }

        return new RestaurantDetailMenuResponse(
                menu.id() == null
                        ? null
                        : String.valueOf(menu.id()),
                menu.menuName(),
                menu.currentPrice(),
                menu.description(),
                menu.imageUrl(),
                menu.recommended()
        );
    }
}
