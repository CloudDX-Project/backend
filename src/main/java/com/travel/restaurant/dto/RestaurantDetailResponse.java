package com.travel.restaurant.dto;

import com.travel.restaurant.data.RestaurantData;
import com.travel.restaurant.data.RestaurantMenuData;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public record RestaurantDetailResponse(

        String id,

        String name,

        String category,

        String address,

        GeoPoint point,

        String representativeImageUrl,

        List<String> imageUrls,

        List<RestaurantDetailMenuResponse> menus,

        Double rating,

        Integer reviewCount,

        String reviewSummary,

        List<String> reviewKeywords,

        String businessHours,

        String phone,

        String placeUrl,

        String naverMapUrl,

        String provider,

        Boolean isMock,

        String sourceLabel,

        LocalDateTime refreshedAt

) {

    public static RestaurantDetailResponse from(
            RestaurantData restaurant,
            List<RestaurantMenuData> menus
    ) {

        List<RestaurantMenuData> safeMenus =
                menus == null
                        ? List.of()
                        : menus;

        List<RestaurantDetailMenuResponse> menuResponses =
                safeMenus.stream()
                        .map(RestaurantDetailMenuResponse::from)
                        .toList();

        /*
         * 상세 화면의 대표 이미지는 restaurants.representative_image_url을
         * 최우선으로 사용한다.
         * 기존 프론트가 imageUrls[0]을 대표 이미지로 사용하므로
         * imageUrls 첫 번째에도 같은 값을 넣어 하위 호환성을 유지한다.
         */
        List<String> imageUrls = new ArrayList<>();

        if (hasText(restaurant.representativeImageUrl())) {
            imageUrls.add(restaurant.representativeImageUrl());
        }

        safeMenus.stream()
                .map(RestaurantMenuData::imageUrl)
                .filter(RestaurantDetailResponse::hasText)
                .filter(imageUrl -> !imageUrls.contains(imageUrl))
                .forEach(imageUrls::add);

        return new RestaurantDetailResponse(
                restaurant.id() == null
                        ? null
                        : String.valueOf(restaurant.id()),
                restaurant.restaurantName(),
                restaurant.category(),
                resolveAddress(
                        restaurant.roadAddress(),
                        restaurant.address()
                ),
                toPoint(
                        restaurant.latitude(),
                        restaurant.longitude()
                ),
                hasText(restaurant.representativeImageUrl())
                        ? restaurant.representativeImageUrl()
                        : null,
                imageUrls.isEmpty()
                        ? null
                        : List.copyOf(imageUrls),
                menuResponses.isEmpty()
                        ? null
                        : menuResponses,
                restaurant.rating(),
                restaurant.reviewCount(),
                restaurant.summary(),

                // 후기 키워드 전용 컬럼이 없으므로 임의 변환하지 않는다.
                null,

                restaurant.businessHours(),
                restaurant.phone(),

                // Kakao Place 원본 URL
                hasText(restaurant.placeUrl())
                        ? restaurant.placeUrl()
                        : null,

                // place_url은 Kakao Place URL이므로 네이버 URL로 복제하지 않는다.
                null,

                null,
                false,
                null,
                restaurant.lastScrapedAt()
        );
    }

    private static GeoPoint toPoint(
            Double latitude,
            Double longitude
    ) {
        if (latitude == null || longitude == null) {
            return null;
        }

        return new GeoPoint(
                latitude,
                longitude
        );
    }

    private static String resolveAddress(
            String roadAddress,
            String address
    ) {
        if (hasText(roadAddress)) {
            return roadAddress;
        }

        return hasText(address)
                ? address
                : null;
    }

    private static boolean hasText(
            String value
    ) {
        return value != null
                && !value.isBlank();
    }

    public record GeoPoint(
            Double latitude,
            Double longitude
    ) {
    }
}
