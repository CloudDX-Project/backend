package com.travel.cafe;

import com.travel.global.util.RecommendationMath;

import com.travel.cafe.data.CafeData;
import com.travel.cafe.repository.CafeRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class CafeCandidateService {

    private static final double
            ESTIMATED_DRIVE_SPEED_KMH =
            45.0;

    /*
     * 일반 여행
     */
    private static final int
            NORMAL_PRIMARY_WINDOW_MINUTES =
            15;

    private static final int
            NORMAL_FALLBACK_WINDOW_MINUTES =
            25;

    /*
     * FOOD 테마
     *
     * 맛집/카페 방문 자체의 중요도가 높으므로
     * 더 먼 곳까지 후보로 인정.
     */
    private static final int
            FOOD_PRIMARY_WINDOW_MINUTES =
            30;

    private static final int
            FOOD_FALLBACK_WINDOW_MINUTES =
            45;

    /*
     * Bayesian Rating prior
     */
    private static final double
            BAYESIAN_PRIOR_COUNT =
            100.0;

    private static final double
            RATING_SCALE =
            5.0;

    private final CafeRepository cafeRepository;

    public CafeCandidateService(
            CafeRepository cafeRepository
    ) {
        this.cafeRepository =
                cafeRepository;
    }

    /*
     * 첫 번째 파라미터 cacheKey만
     * Redis key로 사용한다.
     *
     * CafeRecommendationService에서
     * 위경도 / FOOD 여부 / 후보 수를 조합해서 넘긴다.
     */
    @Cacheable(
            cacheNames = "cafeCandidates",
            key = "#p0"
    )
    @Transactional(readOnly = true)
    public CafeCandidatePool getCandidatePool(

            String cacheKey,

            double originLatitude,

            double originLongitude,

            boolean foodFocused,

            int targetCount

    ) {

        List<CafeData> cafes =
                cafeRepository.findAllLocated();

        if (cafes.isEmpty()) {

            return new CafeCandidatePool(
                    foodFocused
                            ? FOOD_PRIMARY_WINDOW_MINUTES
                            : NORMAL_PRIMARY_WINDOW_MINUTES,
                    new ArrayList<>()
            );
        }

        double globalAverageRating =
                calculateGlobalAverageRating(
                        cafes
                );

        List<CafeScoredCandidate> scored =
                cafes.stream()
                        .map(
                                cafe ->
                                        score(
                                                cafe,
                                                originLatitude,
                                                originLongitude,
                                                foodFocused,
                                                globalAverageRating
                                        )
                        )
                        .toList();

        CandidateWindow window =
                chooseCandidateWindow(
                        scored,
                        targetCount,
                        foodFocused
                );

        ArrayList<CafeScoredCandidate> topCandidates =
                window.candidates()
                        .stream()
                        .sorted(
                                Comparator
                                        .comparingDouble(
                                                CafeScoredCandidate::baseScore
                                        )
                                        .reversed()
                        )
                        .limit(targetCount)
                        .collect(
                                Collectors.toCollection(
                                        ArrayList::new
                                )
                        );

        return new CafeCandidatePool(
                window.windowMinutes(),
                topCandidates
        );
    }

    private CafeScoredCandidate score(

            CafeData cafe,

            double originLatitude,

            double originLongitude,

            boolean foodFocused,

            double globalAverageRating

    ) {

        double distanceKm =
                RecommendationMath.distanceKm(
                        originLatitude,
                        originLongitude,
                        cafe.latitude(),
                        cafe.longitude()
                );

        int estimatedDriveMinutes =
                calculateEstimatedDriveMinutes(
                        distanceKm
                );

        double bayesianRating =
                calculateBayesianRating(
                        cafe,
                        globalAverageRating
                );

        double ratingScore =
                RecommendationMath.clamp(
                        bayesianRating
                                /
                                RATING_SCALE,
                        0.0,
                        1.0
                );

        double distanceScore =
                calculateDistanceScore(
                        estimatedDriveMinutes,
                        foodFocused
                );

        double experienceScore =
                calculateExperienceScore(
                        cafe
                );

        /*
         * 일반 여행
         *
         * 평점/리뷰 Bayesian 품질 65%
         * 거리 15%
         * 카페 특성 20%
         *
         * FOOD 여행
         *
         * 평점/리뷰 Bayesian 품질 60%
         * 거리 10%
         * 카페 특성 30%
         */
        double baseScore;

        if (foodFocused) {

            baseScore =
                    ratingScore * 0.60
                            +
                            distanceScore * 0.10
                            +
                            experienceScore * 0.30;

        } else {

            baseScore =
                    ratingScore * 0.65
                            +
                            distanceScore * 0.15
                            +
                            experienceScore * 0.20;
        }

        return new CafeScoredCandidate(
                cafe,
                RecommendationMath.round(
                        distanceKm,
                        2
                ),
                estimatedDriveMinutes,
                bayesianRating,
                ratingScore,
                distanceScore,
                experienceScore,
                RecommendationMath.clamp(
                        baseScore,
                        0.0,
                        1.0
                )
        );
    }

    private CandidateWindow chooseCandidateWindow(

            List<CafeScoredCandidate> scored,

            int targetCount,

            boolean foodFocused

    ) {

        int primaryMinutes =
                foodFocused
                        ? FOOD_PRIMARY_WINDOW_MINUTES
                        : NORMAL_PRIMARY_WINDOW_MINUTES;

        int fallbackMinutes =
                foodFocused
                        ? FOOD_FALLBACK_WINDOW_MINUTES
                        : NORMAL_FALLBACK_WINDOW_MINUTES;

        List<CafeScoredCandidate> primary =
                scored.stream()
                        .filter(
                                candidate ->
                                        candidate
                                                .estimatedDriveMinutes()
                                                <= primaryMinutes
                        )
                        .toList();

        if (primary.size() >= targetCount) {

            return new CandidateWindow(
                    primaryMinutes,
                    primary
            );
        }

        List<CafeScoredCandidate> fallback =
                scored.stream()
                        .filter(
                                candidate ->
                                        candidate
                                                .estimatedDriveMinutes()
                                                <= fallbackMinutes
                        )
                        .toList();

        if (fallback.size() >= targetCount) {

            return new CandidateWindow(
                    fallbackMinutes,
                    fallback
            );
        }

        /*
         * 후보 부족 시 제주 전체 사용
         */
        return new CandidateWindow(
                0,
                scored
        );
    }

    private double calculateBayesianRating(

            CafeData cafe,

            double globalAverageRating

    ) {

        double rating =
                validRating(
                        cafe.rating()
                )
                        ? cafe.rating()
                        : globalAverageRating;

        double reviewCount =
                cafe.reviewCount() == null
                        ? 0.0
                        : Math.max(
                        cafe.reviewCount(),
                        0
                );

        double result =
                (
                        reviewCount
                                /
                                (
                                        reviewCount
                                                +
                                                BAYESIAN_PRIOR_COUNT
                                )
                )
                        *
                        rating

                        +

                        (
                                BAYESIAN_PRIOR_COUNT
                                        /
                                        (
                                                reviewCount
                                                        +
                                                        BAYESIAN_PRIOR_COUNT
                                        )
                        )
                                *
                                globalAverageRating;

        return RecommendationMath.clamp(
                result,
                0.0,
                RATING_SCALE
        );
    }

    private double calculateGlobalAverageRating(
            List<CafeData> cafes
    ) {

        return cafes.stream()
                .map(
                        CafeData::rating
                )
                .filter(
                        this::validRating
                )
                .mapToDouble(
                        Double::doubleValue
                )
                .average()
                .orElse(
                        3.5
                );
    }

    private boolean validRating(
            Double rating
    ) {

        return rating != null
                &&
                rating > 0
                &&
                rating <= RATING_SCALE;
    }

    private double calculateDistanceScore(

            int estimatedDriveMinutes,

            boolean foodFocused

    ) {

        double maxMinutes =
                foodFocused
                        ? 60.0
                        : 35.0;

        return RecommendationMath.clamp(
                1.0
                        -
                        (
                                estimatedDriveMinutes
                                        /
                                        maxMinutes
                        ),
                0.0,
                1.0
        );
    }

    /*
     * 카페만의 방문 가치 점수.
     *
     * 식당의 제주 향토음식 점수와는 별개.
     */
    private double calculateExperienceScore(
            CafeData cafe
    ) {

        String text =
                (
                        nullToEmpty(
                                cafe.cafeName()
                        )
                                + " "
                                +
                                nullToEmpty(
                                        cafe.category()
                                )
                                + " "
                                +
                                nullToEmpty(
                                        cafe.summary()
                                )
                                + " "
                                +
                                nullToEmpty(
                                        cafe.tags()
                                )
                                + " "
                                +
                                nullToEmpty(
                                        cafe.facilities()
                                )
                )
                        .toLowerCase(
                                Locale.ROOT
                        );

        boolean jejuMenu =
                containsAny(
                        text,
                        List.of(
                                "한라봉",
                                "감귤",
                                "우도땅콩",
                                "우도 땅콩",
                                "제주녹차",
                                "제주 녹차",
                                "말차",
                                "오메기",
                                "제주 디저트"
                        )
                );

        boolean experience =
                containsAny(
                        text,
                        List.of(
                                "오션뷰",
                                "바다뷰",
                                "바다 전망",
                                "루프탑",
                                "정원",
                                "베이커리",
                                "디저트",
                                "로스터리",
                                "대형카페",
                                "대형 카페",
                                "애견동반",
                                "애견 동반"
                        )
                );

        if (jejuMenu && experience) {
            return 1.0;
        }

        if (jejuMenu || experience) {
            return 0.85;
        }

        return 0.60;
    }

    private boolean containsAny(
            String text,
            List<String> keywords
    ) {

        return keywords.stream()
                .anyMatch(
                        text::contains
                );
    }

    private String nullToEmpty(
            String value
    ) {

        return value == null
                ? ""
                : value;
    }

    private int calculateEstimatedDriveMinutes(
            double distanceKm
    ) {

        return (int) Math.ceil(
                distanceKm
                        /
                        ESTIMATED_DRIVE_SPEED_KMH
                        *
                        60.0
        );
    }

    public record CafeCandidatePool(

            int searchWindowMinutes,

            List<CafeScoredCandidate> candidates

    ) implements Serializable {
    }

    public record CafeScoredCandidate(

            CafeData cafe,

            double distanceKm,

            int estimatedDriveMinutes,

            double bayesianRating,

            double ratingScore,

            double distanceScore,

            double experienceScore,

            double baseScore

    ) implements Serializable {
    }

    private record CandidateWindow(

            int windowMinutes,

            List<CafeScoredCandidate> candidates

    ) {
    }
}
