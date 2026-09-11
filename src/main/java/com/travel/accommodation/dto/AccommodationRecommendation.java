package com.travel.accommodation.dto;

public record AccommodationRecommendation(

        Long accommodationId,

        String providerId,

        String name,

        String address,

        String province,

        String city,

        String town,

        Double latitude,

        Double longitude,

        /**
         * 관광지와 숙소 직선거리
         */
        Double distanceKm,

        /**
         * 현재는 직선거리 기반 추정 운전시간
         */
        Integer estimatedDriveMinutes,

        /**
         * Naver Hotel 원본 평점
         */
        Double rating,

        Double ratingScale,

        /**
         * Naver Hotel 리뷰 수
         */
        Integer reviewCount,

        /**
         * 리뷰 수로 신뢰도를 보정한 평점.
         * 10점 만점.
         */
        Double bayesianRating,

        /**
         * 최종 추천점수.
         * 0 ~ 100
         */
        Double recommendationScore,

        Integer starCount,

        /**
         * 평균 숙박 가격.
         *
         * 일부 숙소는 null일 수 있음.
         * 가격 계산 / 정렬용 보조 데이터.
         */
        Long priceAvg,

        /**
         * 네이버에서 수집한 가격 표시 문자열.
         *
         * 화면 표시용 가격 데이터.
         */
        String priceText,

        String representativeImageUrl,

        String providerUrl,

        String checkInTime,

        String checkOutTime,

        String description,

        String phoneNumber

) {
}