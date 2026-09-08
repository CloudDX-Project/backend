package com.travel.attraction.dto;

import java.util.List;

public record AttractionSearchResponse(

        String region1Name,

        String region2Name,

        String keyword,

        int count,

        List<AttractionCandidate> attractions

) {
}