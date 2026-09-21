package com.travel.cafe.dto;

import java.util.List;

public record CafeRecommendResponse(

        Double originLatitude,

        Double originLongitude,

        boolean foodFocused,

        int requestedLimit,

        int searchWindowMinutes,

        String travelTimeBasis,

        int candidatePoolCount,

        List<CafeRecommendation> cafes

) {
}