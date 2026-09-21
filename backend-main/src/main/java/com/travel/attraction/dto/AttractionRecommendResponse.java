package com.travel.attraction.dto;

import java.util.List;

public record AttractionRecommendResponse(

        Double latitude,
        Double longitude,

        int requestedLimit,

        int searchWindowMinutes,

        String travelTimeBasis,

        int candidatePoolCount,

        List<AttractionRecommendation> attractions

) {
}