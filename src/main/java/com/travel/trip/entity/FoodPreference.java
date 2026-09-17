package com.travel.trip.entity;

import java.util.List;
import java.util.Locale;

public enum FoodPreference {

    KOREAN(
            List.of(
                    "한식"
            )
    ),

    JAPANESE(
            List.of(
                    "일식"
            )
    ),

    WESTERN(
            List.of(
                    "양식"
            )
    ),

    CHINESE(
            List.of(
                    "중식"
            )
    ),

    ASIAN(
            List.of(
                    "아시안",
                    "아시아",
                    "동남아"
            )
    ),

    SNACK(
            List.of(
                    "분식",
                    "간식"
            )
    ),

    CAFE(
            List.of(
                    "카페",
                    "디저트",
                    "베이커리"
            )
    );

    private final List<String> categoryKeywords;

    FoodPreference(
            List<String> categoryKeywords
    ) {
        this.categoryKeywords =
                categoryKeywords;
    }

    public List<String> getCategoryKeywords() {
        return categoryKeywords;
    }

    /**
     * DB category 예:
     *
     * 음식점 > 한식
     * 음식점 > 양식
     * 음식점 > 분식
     * 음식점 > 간식
     */
    public boolean matchesCategory(
            String category
    ) {

        if (category == null
                || category.isBlank()) {
            return false;
        }

        String normalizedCategory =
                normalize(category);

        return categoryKeywords
                .stream()
                .map(this::normalize)
                .anyMatch(
                        normalizedCategory::contains
                );
    }

    private String normalize(
            String value
    ) {

        return value
                .replace(" ", "")
                .toLowerCase(Locale.ROOT);
    }
}