package com.travel.attraction.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AttractionSearchRequest(

        String region1Name,

        String region2Name,

        String keyword,

        @Min(1)
        @Max(100)
        Integer limit

) {

    public int resolvedLimit() {
        return limit == null
                ? 50
                : limit;
    }
}