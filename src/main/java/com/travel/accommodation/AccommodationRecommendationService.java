package com.travel.accommodation;

import com.travel.accommodation.data.AccommodationEnrichmentData;
import com.travel.accommodation.dto.AccommodationRecommendRequest;
import com.travel.accommodation.dto.AccommodationRecommendResponse;
import com.travel.accommodation.dto.AccommodationRecommendation;
import com.travel.accommodation.repository.AccommodationEnrichmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class AccommodationRecommendationService {

    private static final String NAVER_PROVIDER = "NAVER_HOTEL";

    /**
     * 현재 실제 길찾기 API를 사용하지 않기 때문에
     * 직선거리를 기반으로 운전시간을 추정한다.
     *
     * 45km/h 기준
     * 30분 ≒ 22.5km
     */
    private static final double ESTIMATED_DRIVE_SPEED_KMH = 45.0;

    /**
     * 기본 추천 범위
     */
    private static final int PRIMARY_WINDOW_MINUTES = 30;

    /**
     * 30분 이내에 숙소가 부족할 때 확장
     */
    private static final int FALLBACK_WINDOW_MINUTES = 45;

    /**
     * Bayesian Rating의 prior weight.
     *
     * 리뷰 100개일 때:
     * 실제 호텔 평점 50%
     * 전체 평균 평점 50%
     */
    private static final double BAYESIAN_PRIOR_REVIEW_COUNT = 100.0;

    /**
     * 추천 가중치
     */
    private static final double DRIVE_WEIGHT = 0.35;
    private static final double RATING_WEIGHT = 0.65;

    private final AccommodationEnrichmentRepository enrichmentRepository;

    public AccommodationRecommendationService(
            AccommodationEnrichmentRepository enrichmentRepository
    ) {
        this.enrichmentRepository = enrichmentRepository;
    }

    @Transactional(readOnly = true)
    public AccommodationRecommendResponse recommend(
            AccommodationRecommendRequest request
    ) {

        int limit = request.resolvedLimit();

        /*
         * 현재 NAVER_HOTEL 392개 조회
         */
        List<AccommodationEnrichmentData> hotels =
                enrichmentRepository
                        .findAllByProvider(NAVER_PROVIDER)
                        .stream()
                        .filter(this::hasCoordinates)
                        .toList();

        if (hotels.isEmpty()) {

            return new AccommodationRecommendResponse(
                    request.destinationName(),
                    request.latitude(),
                    request.longitude(),
                    limit,
                    PRIMARY_WINDOW_MINUTES,
                    "STRAIGHT_LINE_ESTIMATE",
                    0,
                    List.of()
            );
        }

        /*
         * 전체 Naver Hotel의 평균 평점.
         * Bayesian Rating의 사전 평균으로 사용.
         */
        double globalAverageRatingScore =
                calculateGlobalAverageRatingScore(hotels);

        /*
         * 모든 숙소 추천점수 계산
         */
        List<ScoredAccommodation> scored =
                hotels.stream()
                        .map(hotel ->
                                score(
                                        hotel,
                                        request.latitude(),
                                        request.longitude(),
                                        globalAverageRatingScore
                                )
                        )
                        .toList();

        /*
         * 30분 → 45분 → 전체 fallback
         */
        CandidateWindow candidateWindow =
                chooseCandidateWindow(
                        scored,
                        limit
                );

        /*
         * 추천점수 기준 TOP N
         */
        List<AccommodationRecommendation> recommendations =
                candidateWindow
                        .candidates()
                        .stream()
                        .sorted(RECOMMENDATION_COMPARATOR)
                        .limit(limit)
                        .map(ScoredAccommodation::recommendation)
                        .toList();

        return new AccommodationRecommendResponse(
                request.destinationName(),
                request.latitude(),
                request.longitude(),
                limit,
                candidateWindow.windowMinutes(),
                "STRAIGHT_LINE_ESTIMATE",
                candidateWindow.candidates().size(),
                recommendations
        );
    }

    /**
     * 30분 이내 후보가 limit 이상이면
     * 30분 내에서 추천.
     *
     * 부족하면 45분.
     *
     * 그래도 부족하면 전체 후보 사용.
     */
    private CandidateWindow chooseCandidateWindow(
            List<ScoredAccommodation> scored,
            int limit
    ) {

        List<ScoredAccommodation> within30 =
                scored.stream()
                        .filter(item ->
                                item.recommendation()
                                        .estimatedDriveMinutes()
                                        <= PRIMARY_WINDOW_MINUTES
                        )
                        .toList();

        if (within30.size() >= limit) {

            return new CandidateWindow(
                    PRIMARY_WINDOW_MINUTES,
                    within30
            );
        }

        List<ScoredAccommodation> within45 =
                scored.stream()
                        .filter(item ->
                                item.recommendation()
                                        .estimatedDriveMinutes()
                                        <= FALLBACK_WINDOW_MINUTES
                        )
                        .toList();

        if (within45.size() >= limit) {

            return new CandidateWindow(
                    FALLBACK_WINDOW_MINUTES,
                    within45
            );
        }

        /*
         * 45분 안에도 limit개가 없으면
         * 제주 전체에서 추천
         */
        return new CandidateWindow(
                0,
                scored
        );
    }

    /**
     * 하나의 호텔 점수 계산
     */
    private ScoredAccommodation score(
            AccommodationEnrichmentData hotel,
            double destinationLatitude,
            double destinationLongitude,
            double globalAverageRatingScore
    ) {

        /*
         * 관광지 ↔ 숙소 직선거리
         */
        double distanceKm =
                calculateDistanceKm(
                        destinationLatitude,
                        destinationLongitude,
                        hotel.providerLatitude(),
                        hotel.providerLongitude()
                );

        /*
         * 직선거리 기반 예상 운전시간
         */
        int estimatedDriveMinutes =
                calculateEstimatedDriveMinutes(
                        distanceKm
                );

        /*
         * 위치 점수
         */
        double driveScore =
                calculateDriveScore(
                        estimatedDriveMinutes
                );

        /*
         * 평점 + 리뷰수 Bayesian 보정
         */
        double bayesianRatingScore =
                calculateBayesianRatingScore(
                        hotel,
                        globalAverageRatingScore
                );

        /*
         * 최종 추천점수
         *
         * 위치 35%
         * 평점 신뢰도 65%
         */
        double recommendationScore =
                driveScore * DRIVE_WEIGHT
                        + bayesianRatingScore * RATING_WEIGHT;

        AccommodationRecommendation recommendation =
                new AccommodationRecommendation(

                        hotel.accommodationId(),

                        hotel.providerId(),

                        hotel.providerName(),

                        hotel.providerAddress(),

                        hotel.province(),

                        hotel.city(),

                        hotel.town(),

                        hotel.providerLatitude(),

                        hotel.providerLongitude(),

                        round(
                                distanceKm,
                                2
                        ),

                        estimatedDriveMinutes,

                        hotel.rating(),

                        hotel.ratingScale(),

                        hotel.reviewCount(),

                        /*
                         * 0~1 score를 다시 10점 만점으로 표시
                         */
                        round(
                                bayesianRatingScore * 10.0,
                                2
                        ),

                        /*
                         * 프론트 표시용 0~100
                         */
                        round(
                                recommendationScore * 100.0,
                                2
                        ),

                        hotel.starCount(),

                        hotel.representativeImageUrl(),

                        hotel.providerUrl(),

                        hotel.checkInTime(),

                        hotel.checkOutTime(),

                        hotel.description(),

                        hotel.phoneNumber()
                );

        return new ScoredAccommodation(
                recommendation,
                bayesianRatingScore,
                recommendationScore
        );
    }

    /**
     * Bayesian Rating
     *
     * 리뷰가 많을수록 실제 숙소 평점을 강하게 신뢰한다.
     *
     * 리뷰가 적으면 전체 평균 평점에 가까워진다.
     */
    private double calculateBayesianRatingScore(
            AccommodationEnrichmentData hotel,
            double globalAverageRatingScore
    ) {

        double ratingScore =
                normalizedRatingOrDefault(
                        hotel,
                        globalAverageRatingScore
                );

        int reviewCount =
                hotel.reviewCount() == null
                        ? 0
                        : Math.max(
                        hotel.reviewCount(),
                        0
                );

        double reviewWeight =
                reviewCount
                        / (
                        reviewCount
                                + BAYESIAN_PRIOR_REVIEW_COUNT
                );

        double priorWeight =
                BAYESIAN_PRIOR_REVIEW_COUNT
                        / (
                        reviewCount
                                + BAYESIAN_PRIOR_REVIEW_COUNT
                );

        return reviewWeight * ratingScore
                + priorWeight * globalAverageRatingScore;
    }

    /**
     * 전체 숙소 평균 평점.
     *
     * rating / ratingScale로 0~1 정규화.
     */
    private double calculateGlobalAverageRatingScore(
            List<AccommodationEnrichmentData> hotels
    ) {

        return hotels.stream()
                .filter(this::hasValidRating)
                .mapToDouble(
                        hotel ->
                                hotel.rating()
                                        / hotel.ratingScale()
                )
                .average()
                .orElse(0.8);
    }

    /**
     * 평점이 없으면 전체 평균값 사용.
     */
    private double normalizedRatingOrDefault(
            AccommodationEnrichmentData hotel,
            double defaultScore
    ) {

        if (!hasValidRating(hotel)) {
            return defaultScore;
        }

        return clamp(
                hotel.rating()
                        / hotel.ratingScale(),
                0.0,
                1.0
        );
    }

    /**
     * 운전시간 점수
     *
     * 0분  -> 1.00
     * 15분 -> 0.75
     * 30분 -> 0.50
     * 45분 -> 0.25
     * 60분 -> 0.00
     */
    private double calculateDriveScore(
            int estimatedDriveMinutes
    ) {

        return clamp(
                1.0
                        - (
                        estimatedDriveMinutes
                                / 60.0
                ),
                0.0,
                1.0
        );
    }

    /**
     * 직선거리 기반 예상 운전시간.
     *
     * 현재는 45km/h를 사용.
     */
    private int calculateEstimatedDriveMinutes(
            double distanceKm
    ) {

        double hours =
                distanceKm
                        / ESTIMATED_DRIVE_SPEED_KMH;

        return Math.max(
                1,
                (int) Math.ceil(
                        hours * 60.0
                )
        );
    }

    /**
     * Haversine 공식.
     *
     * 두 위경도의 직선거리 km 계산.
     */
    private double calculateDistanceKm(
            double lat1,
            double lon1,
            double lat2,
            double lon2
    ) {

        final double earthRadiusKm =
                6371.0;

        double latDistance =
                Math.toRadians(
                        lat2 - lat1
                );

        double lonDistance =
                Math.toRadians(
                        lon2 - lon1
                );

        double a =
                Math.sin(
                        latDistance / 2.0
                )
                        * Math.sin(
                        latDistance / 2.0
                )
                        +
                        Math.cos(
                                Math.toRadians(lat1)
                        )
                                * Math.cos(
                                Math.toRadians(lat2)
                        )
                                * Math.sin(
                                lonDistance / 2.0
                        )
                                * Math.sin(
                                lonDistance / 2.0
                        );

        double c =
                2.0
                        * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(
                                1.0 - a
                        )
                );

        return earthRadiusKm * c;
    }

    private boolean hasCoordinates(
            AccommodationEnrichmentData hotel
    ) {

        return hotel.providerLatitude() != null
                && hotel.providerLongitude() != null;
    }

    private boolean hasValidRating(
            AccommodationEnrichmentData hotel
    ) {

        return hotel.rating() != null
                && hotel.ratingScale() != null
                && hotel.ratingScale() > 0.0;
    }

    private double clamp(
            double value,
            double min,
            double max
    ) {

        return Math.max(
                min,
                Math.min(
                        max,
                        value
                )
        );
    }

    private double round(
            double value,
            int digits
    ) {

        double scale =
                Math.pow(
                        10,
                        digits
                );

        return Math.round(
                value * scale
        ) / scale;
    }

    /**
     * 정렬 기준
     *
     * 1. 최종 추천점수 높은 순
     * 2. Bayesian 평점 높은 순
     * 3. 예상 이동시간 짧은 순
     * 4. 리뷰 수 많은 순
     */
    private static final Comparator<ScoredAccommodation>
            RECOMMENDATION_COMPARATOR =

            Comparator.comparingDouble(
                            ScoredAccommodation::recommendationScore
                    )
                    .reversed()

                    .thenComparing(
                            Comparator.comparingDouble(
                                            ScoredAccommodation
                                                    ::bayesianRatingScore
                                    )
                                    .reversed()
                    )

                    .thenComparingInt(
                            item ->
                                    item.recommendation()
                                            .estimatedDriveMinutes()
                    )

                    .thenComparing(
                            item ->
                                    item.recommendation()
                                            .reviewCount()
                                            == null
                                            ? 0
                                            : item.recommendation()
                                            .reviewCount(),

                            Comparator.reverseOrder()
                    );

    private record ScoredAccommodation(

            AccommodationRecommendation recommendation,

            double bayesianRatingScore,

            double recommendationScore

    ) {
    }

    private record CandidateWindow(

            int windowMinutes,

            List<ScoredAccommodation> candidates

    ) {
    }
}