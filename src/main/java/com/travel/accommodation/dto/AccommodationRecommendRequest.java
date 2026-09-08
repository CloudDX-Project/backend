package com.travel.accommodation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AccommodationRecommendRequest(

        String destinationName,

        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        Double latitude,

        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        Double longitude,

        @Min(1)
        @Max(30)
        Integer limit

) {

    /**
     * 프론트에서 limit 미전달 시
     * 기본 12개 추천.
     */
    public int resolvedLimit() {
        return limit == null
                ? 12
                : limit;
    }
}