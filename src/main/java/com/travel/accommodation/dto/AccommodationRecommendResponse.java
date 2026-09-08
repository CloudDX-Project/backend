package com.travel.accommodation.dto;

import java.util.List;

public record AccommodationRecommendResponse(

        String destinationName,

        Double latitude,

        Double longitude,

        int requestedLimit,

        /**
         * 실제 추천에 사용된 범위.
         *
         * 30
         * 45
         * 0 = 전체 fallback
         */
        int searchWindowMinutes,

        /**
         * 현재:
         * STRAIGHT_LINE_ESTIMATE
         *
         * 추후 길찾기 API 사용하면
         * ROUTING_API 등으로 변경 가능
         */
        String travelTimeBasis,

        /**
         * 추천 정렬 전 후보 개수
         */
        int totalCandidateCount,

        List<AccommodationRecommendation> accommodations

) {
}