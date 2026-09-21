package com.travel.cafe.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CafeSearchRequest(

        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        Double originLatitude,

        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        Double originLongitude,

        @Min(1)
        @Max(100)
        Integer limit

) {

    public int resolvedLimit() {

        return limit == null
                ? 30
                : limit;
    }
}