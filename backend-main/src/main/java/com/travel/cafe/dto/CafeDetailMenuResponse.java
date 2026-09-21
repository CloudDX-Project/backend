package com.travel.cafe.dto;

import com.travel.cafe.data.CafeMenuData;

public record CafeDetailMenuResponse(

        String id,

        String name,

        Integer price,

        String description,

        String imageUrl,

        Boolean isSignature

) {

    public static CafeDetailMenuResponse from(
            CafeMenuData menu
    ) {
        if (menu == null) {
            return null;
        }

        return new CafeDetailMenuResponse(
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
