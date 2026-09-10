package com.travel.cafe.dto;

import java.util.List;

public record CafeSearchResponse(

        Double originLatitude,

        Double originLongitude,

        int count,

        String travelTimeBasis,

        List<CafeCandidate> cafes

) {
}