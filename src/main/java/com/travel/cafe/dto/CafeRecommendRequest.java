package com.travel.cafe.dto;

import com.travel.trip.entity.TripPreference;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CafeRecommendRequest(

        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        Double originLatitude,

        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        Double originLongitude,

        @Size(
                max = 3,
                message = "여행 테마는 최대 3개까지 선택할 수 있습니다."
        )
        Set<TripPreference> preferences,

        @Min(1)
        @Max(15)
        Integer limit

) {

    public Set<TripPreference> resolvedPreferences() {

        return preferences == null
                ? Set.of()
                : preferences;
    }

    /*
     * TripPreference에 CAFE는 없음.
     *
     * FOOD 여부만 확인해서
     * 맛집 중심 여행인지 판단한다.
     */
    public boolean foodFocused() {

        return resolvedPreferences()
                .contains(
                        TripPreference.FOOD
                );
    }

    public int resolvedLimit() {

        return limit == null
                ? 10
                : limit;
    }
}