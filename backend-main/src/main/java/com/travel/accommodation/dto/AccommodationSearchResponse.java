package com.travel.accommodation.dto;

import java.util.List;

public record AccommodationSearchResponse(

        String province,

        String city,

        String town,

        int rawCount,

        int deduplicatedCount,

        List<AccommodationCandidate> accommodations

) {
}