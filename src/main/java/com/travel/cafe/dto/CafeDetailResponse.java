package com.travel.cafe.dto;

import com.travel.cafe.data.CafeData;
import com.travel.cafe.data.CafeMenuData;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public record CafeDetailResponse(

        String id,

        String name,

        String category,

        String address,

        GeoPoint point,

        String representativeImageUrl,

        List<String> imageUrls,

        List<CafeDetailMenuResponse> menus,

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

    public static CafeDetailResponse from(
            CafeData cafe,
            List<CafeMenuData> menus
    ) {

        List<CafeMenuData> safeMenus =
                menus == null
                        ? List.of()
                        : menus;

        List<CafeDetailMenuResponse> menuResponses =
                safeMenus.stream()
                        .map(CafeDetailMenuResponse::from)
                        .toList();

        /*
         * 상세 화면의 대표 이미지는 cafes.representative_image_url을
         * 최우선으로 사용한다.
         */
        List<String> imageUrls = new ArrayList<>();

        if (hasText(cafe.representativeImageUrl())) {
            imageUrls.add(cafe.representativeImageUrl());
        }

        safeMenus.stream()
                .map(CafeMenuData::imageUrl)
                .filter(CafeDetailResponse::hasText)
                .filter(imageUrl -> !imageUrls.contains(imageUrl))
                .forEach(imageUrls::add);

        return new CafeDetailResponse(
                cafe.id() == null
                        ? null
                        : String.valueOf(cafe.id()),
                cafe.cafeName(),
                cafe.category(),
                resolveAddress(
                        cafe.roadAddress(),
                        cafe.address()
                ),
                toPoint(
                        cafe.latitude(),
                        cafe.longitude()
                ),
                hasText(cafe.representativeImageUrl())
                        ? cafe.representativeImageUrl()
                        : null,
                imageUrls.isEmpty()
                        ? null
                        : List.copyOf(imageUrls),
                menuResponses.isEmpty()
                        ? null
                        : menuResponses,
                cafe.rating(),
                cafe.reviewCount(),
                cafe.summary(),

                // 후기 키워드 전용 컬럼이 없으므로 null.
                null,

                cafe.businessHours(),
                cafe.phone(),

                // Kakao Place 원본 URL
                hasText(cafe.placeUrl())
                        ? cafe.placeUrl()
                        : null,

                null,
                null,
                false,
                null,
                cafe.lastScrapedAt()
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
