package com.travel.restaurant;

import com.travel.external.bedrock.BedrockClient;
import com.travel.restaurant.data.RestaurantData;
import com.travel.restaurant.data.RestaurantMenuData;
import com.travel.restaurant.dto.RestaurantRecommendRequest;
import com.travel.restaurant.dto.RestaurantRecommendResponse;
import com.travel.restaurant.dto.RestaurantRecommendation;
import com.travel.restaurant.repository.RestaurantRepository;
import com.travel.trip.entity.FoodPreference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RestaurantRecommendationService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    RestaurantRecommendationService.class
            );

    /*
     * 실제 TMAP/Kakao Routing 전 임시 계산.
     */
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
     * FOOD 테마 여행
     *
     * 맛집을 위해 좀 더 멀리 이동하는 것을 허용.
     */
    private static final int
            FOOD_PRIMARY_WINDOW_MINUTES =
            30;

    private static final int
            FOOD_FALLBACK_WINDOW_MINUTES =
            45;

    private static final int
            MIN_AI_CANDIDATE_POOL =
            30;

    private static final int
            MAX_AI_CANDIDATE_POOL =
            50;

    /*
     * Bayesian Rating prior count.
     */
    private static final double
            BAYESIAN_PRIOR_COUNT =
            100.0;

    /*
     * Kakao rating 기준.
     */
    private static final double
            RATING_SCALE =
            5.0;

    /*
     * Bedrock 최종 결합.
     */
    private static final double
            BASE_FINAL_WEIGHT =
            0.45;

    private static final double
            AI_FINAL_WEIGHT =
            0.55;

    private final RestaurantRepository restaurantRepository;

    private final BedrockClient bedrockClient;

    private final JsonMapper jsonMapper;

    public RestaurantRecommendationService(
            RestaurantRepository restaurantRepository,
            BedrockClient bedrockClient,
            JsonMapper jsonMapper
    ) {

        this.restaurantRepository =
                restaurantRepository;

        this.bedrockClient =
                bedrockClient;

        this.jsonMapper =
                jsonMapper;
    }

    @Transactional(readOnly = true)
    public RestaurantRecommendResponse recommend(
            RestaurantRecommendRequest request
    ) {

        int limit =
                request.resolvedLimit();

        boolean foodFocused =
                request.foodFocused();

        int candidatePoolSize =
                Math.min(
                        MAX_AI_CANDIDATE_POOL,
                        Math.max(
                                MIN_AI_CANDIDATE_POOL,
                                limit * 3
                        )
                );

        List<RestaurantData> allRestaurants =
                restaurantRepository
                        .findAllLocated();

        /*
         * 사용자가 음식 취향을 골랐다면
         * DB category 기준으로 우선 필터.
         */
        List<RestaurantData> categoryMatched =
                filterByFoodPreferences(
                        allRestaurants,
                        request.resolvedFoodPreferences()
                );

        if (categoryMatched.isEmpty()) {

            return new RestaurantRecommendResponse(

                    request.originLatitude(),

                    request.originLongitude(),

                    foodFocused,

                    request.resolvedFoodPreferences(),

                    limit,

                    foodFocused
                            ? FOOD_PRIMARY_WINDOW_MINUTES
                            : NORMAL_PRIMARY_WINDOW_MINUTES,

                    "STRAIGHT_LINE_ESTIMATE",

                    0,

                    List.of()
            );
        }

        double globalAverageRating =
                calculateGlobalAverageRating(
                        allRestaurants
                );

        List<ScoredRestaurant> scored =
                categoryMatched
                        .stream()
                        .map(
                                restaurant ->
                                        score(
                                                restaurant,
                                                request,
                                                globalAverageRating
                                        )
                        )
                        .toList();

        CandidateWindow candidateWindow =
                chooseCandidateWindow(
                        scored,
                        candidatePoolSize,
                        foodFocused
                );

        List<ScoredRestaurant> aiCandidates =
                candidateWindow
                        .candidates()
                        .stream()
                        .sorted(
                                Comparator
                                        .comparingDouble(
                                                ScoredRestaurant::baseScore
                                        )
                                        .reversed()
                        )
                        .limit(
                                candidatePoolSize
                        )
                        .toList();

        List<Long> candidateIds =
                aiCandidates
                        .stream()
                        .map(
                                item ->
                                        item.restaurant().id()
                        )
                        .toList();

        Map<Long, List<RestaurantMenuData>> menuMap =
                restaurantRepository
                        .findMenusByRestaurantIds(
                                candidateIds
                        );

        List<AiDecision> aiDecisions;

        try {

            aiDecisions =
                    rerankWithBedrock(
                            request,
                            aiCandidates,
                            menuMap,
                            limit
                    );

        } catch (Exception e) {

            log.warn(
                    "음식점 Bedrock reranking 실패. "
                            + "정량 추천 결과로 fallback 합니다.",
                    e
            );

            aiDecisions =
                    List.of();
        }

        List<RestaurantRecommendation> recommendations =
                buildFinalRecommendations(
                        aiCandidates,
                        aiDecisions,
                        menuMap,
                        request.resolvedFoodPreferences(),
                        limit
                );

        return new RestaurantRecommendResponse(

                request.originLatitude(),

                request.originLongitude(),

                foodFocused,

                request.resolvedFoodPreferences(),

                limit,

                candidateWindow.windowMinutes(),

                "STRAIGHT_LINE_ESTIMATE",

                aiCandidates.size(),

                recommendations
        );
    }

    /**
     * FoodPreference는 추천 점수가 아니라
     * DB category 필터로 사용.
     *
     * 예:
     *
     * KOREAN
     *   -> 음식점 > 한식
     *
     * SNACK
     *   -> 음식점 > 분식
     *   -> 음식점 > 간식
     */
    private List<RestaurantData>
    filterByFoodPreferences(
            List<RestaurantData> restaurants,
            Set<FoodPreference> foodPreferences
    ) {

        if (foodPreferences == null
                || foodPreferences.isEmpty()) {

            return restaurants;
        }

        return restaurants
                .stream()
                .filter(
                        restaurant ->
                                foodPreferences
                                        .stream()
                                        .anyMatch(
                                                preference ->
                                                        preference
                                                                .matchesCategory(
                                                                        restaurant.category()
                                                                )
                                        )
                )
                .toList();
    }

    private ScoredRestaurant score(
            RestaurantData restaurant,
            RestaurantRecommendRequest request,
            double globalAverageRating
    ) {

        double distanceKm =
                calculateDistanceKm(
                        request.originLatitude(),
                        request.originLongitude(),
                        restaurant.latitude(),
                        restaurant.longitude()
                );

        int estimatedDriveMinutes =
                calculateEstimatedDriveMinutes(
                        distanceKm
                );

        double bayesianRating =
                calculateBayesianRating(
                        restaurant,
                        globalAverageRating
                );

        double ratingScore =
                clamp(
                        bayesianRating
                                / RATING_SCALE,
                        0.0,
                        1.0
                );

        double distanceScore =
                calculateDistanceScore(
                        estimatedDriveMinutes,
                        request.foodFocused()
                );

        double localScore =
                calculateLocalScore(
                        restaurant
                );

        /*
         * FOOD 여행이면:
         *
         * 평점 55%
         * 거리 20%
         * 제주 로컬성 25%
         *
         * 일반 여행이면:
         *
         * 평점 50%
         * 거리 40%
         * 제주 로컬성 10%
         */
        double baseScore;

        if (request.foodFocused()) {

            baseScore =
                    ratingScore * 0.55
                            +
                            distanceScore * 0.20
                            +
                            localScore * 0.25;

        } else {

            baseScore =
                    ratingScore * 0.50
                            +
                            distanceScore * 0.40
                            +
                            localScore * 0.10;
        }

        return new ScoredRestaurant(

                restaurant,

                round(
                        distanceKm,
                        2
                ),

                estimatedDriveMinutes,

                bayesianRating,

                ratingScore,

                distanceScore,

                localScore,

                clamp(
                        baseScore,
                        0.0,
                        1.0
                )
        );
    }

    /**
     * Bayesian Rating
     *
     * 리뷰 몇 개 없는 5.0점 음식점이
     * 리뷰 수천 개의 4.7점을 무조건 이기는 문제 방지.
     */
    private double calculateBayesianRating(
            RestaurantData restaurant,
            double globalAverageRating
    ) {

        double rating =
                validRating(
                        restaurant.rating()
                )
                        ? restaurant.rating()
                        : globalAverageRating;

        double reviewCount =
                restaurant.reviewCount() == null
                        ? 0.0
                        : Math.max(
                        0,
                        restaurant.reviewCount()
                );

        double weightedRating =
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

        return clamp(
                weightedRating,
                0.0,
                RATING_SCALE
        );
    }

    private double calculateGlobalAverageRating(
            List<RestaurantData> restaurants
    ) {

        return restaurants
                .stream()
                .map(
                        RestaurantData::rating
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
                && rating > 0.0
                && rating <= RATING_SCALE;
    }

    private double calculateDistanceScore(
            int estimatedDriveMinutes,
            boolean foodFocused
    ) {

        double maxMinutes =
                foodFocused
                        ? 60.0
                        : 35.0;

        return clamp(
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

    /**
     * 제주 음식 특성이 보이면 우대.
     *
     * address를 검사하면 모든 제주 음식점이
     * 점수를 받기 때문에 address는 사용하지 않음.
     */
    private double calculateLocalScore(
            RestaurantData restaurant
    ) {

        String text =
                (
                        nullToEmpty(
                                restaurant.restaurantName()
                        )
                                + " "
                                +
                                nullToEmpty(
                                        restaurant.category()
                                )
                                + " "
                                +
                                nullToEmpty(
                                        restaurant.summary()
                                )
                                + " "
                                +
                                nullToEmpty(
                                        restaurant.tags()
                                )
                )
                        .toLowerCase(
                                Locale.ROOT
                        );

        boolean local =
                containsAny(
                        text,
                        List.of(
                                "흑돼지",
                                "고기국수",
                                "갈치",
                                "갈치조림",
                                "갈치구이",
                                "전복",
                                "성게",
                                "몸국",
                                "해녀",
                                "옥돔",
                                "자리돔",
                                "한치",
                                "제주향토",
                                "향토음식",
                                "제주식"
                        )
                );

        return local
                ? 1.0
                : 0.5;
    }

    private CandidateWindow chooseCandidateWindow(
            List<ScoredRestaurant> scored,
            int targetCount,
            boolean foodFocused
    ) {

        int primaryWindow =
                foodFocused
                        ? FOOD_PRIMARY_WINDOW_MINUTES
                        : NORMAL_PRIMARY_WINDOW_MINUTES;

        int fallbackWindow =
                foodFocused
                        ? FOOD_FALLBACK_WINDOW_MINUTES
                        : NORMAL_FALLBACK_WINDOW_MINUTES;

        List<ScoredRestaurant> primary =
                scored
                        .stream()
                        .filter(
                                restaurant ->
                                        restaurant
                                                .estimatedDriveMinutes()
                                                <= primaryWindow
                        )
                        .toList();

        if (primary.size() >= targetCount) {

            return new CandidateWindow(
                    primaryWindow,
                    primary
            );
        }

        List<ScoredRestaurant> fallback =
                scored
                        .stream()
                        .filter(
                                restaurant ->
                                        restaurant
                                                .estimatedDriveMinutes()
                                                <= fallbackWindow
                        )
                        .toList();

        if (fallback.size() >= targetCount) {

            return new CandidateWindow(
                    fallbackWindow,
                    fallback
            );
        }

        /*
         * 후보 자체가 적으면 전체 category 후보 사용.
         */
        return new CandidateWindow(
                0,
                scored
        );
    }

    private List<AiDecision> rerankWithBedrock(
            RestaurantRecommendRequest request,
            List<ScoredRestaurant> candidates,
            Map<Long, List<RestaurantMenuData>> menuMap,
            int limit
    ) throws JacksonException {

        List<Map<String, Object>> candidateJson =
                new ArrayList<>();

        for (ScoredRestaurant scored : candidates) {

            RestaurantData restaurant =
                    scored.restaurant();

            Map<String, Object> candidate =
                    new LinkedHashMap<>();

            candidate.put(
                    "restaurantId",
                    restaurant.id()
            );

            candidate.put(
                    "name",
                    restaurant.restaurantName()
            );

            candidate.put(
                    "category",
                    restaurant.category()
            );

            candidate.put(
                    "rating",
                    restaurant.rating()
            );

            candidate.put(
                    "reviewCount",
                    restaurant.reviewCount()
            );

            candidate.put(
                    "bayesianRating",
                    round(
                            scored.bayesianRating(),
                            2
                    )
            );

            candidate.put(
                    "distanceKm",
                    scored.distanceKm()
            );

            candidate.put(
                    "estimatedDriveMinutes",
                    scored.estimatedDriveMinutes()
            );

            candidate.put(
                    "summary",
                    truncate(
                            restaurant.summary(),
                            180
                    )
            );

            candidate.put(
                    "tags",
                    truncate(
                            restaurant.tags(),
                            200
                    )
            );

            candidate.put(
                    "baseScore",
                    round(
                            scored.baseScore()
                                    * 100.0,
                            2
                    )
            );

            List<Map<String, Object>> menus =
                    menuMap
                            .getOrDefault(
                                    restaurant.id(),
                                    List.of()
                            )
                            .stream()
                            .limit(8)
                            .map(
                                    menu -> {

                                        Map<String, Object> menuJson =
                                                new LinkedHashMap<>();

                                        menuJson.put(
                                                "menuName",
                                                menu.menuName()
                                        );

                                        menuJson.put(
                                                "price",
                                                menu.currentPrice()
                                        );

                                        menuJson.put(
                                                "recommended",
                                                menu.recommended()
                                        );

                                        menuJson.put(
                                                "menuTags",
                                                truncate(
                                                        menu.menuTags(),
                                                        100
                                                )
                                        );

                                        return menuJson;
                                    }
                            )
                            .toList();

            candidate.put(
                    "menus",
                    menus
            );

            candidateJson.add(
                    candidate
            );
        }

        String candidateJsonString =
                jsonMapper
                        .writeValueAsString(
                                candidateJson
                        );

        String prompt =
                """
                너는 제주 여행 음식점 추천 reranker다.

                아래 candidates는 실제 DB에 존재하는 음식점만 포함한다.

                반드시 candidates에 존재하는 restaurantId만 반환한다.
                존재하지 않는 음식점이나 restaurantId를 절대로 생성하지 않는다.

                사용자 음식 취향:
                %s

                여행 전체 테마:
                %s

                FOOD 중심 여행 여부:
                %s

                FOOD 중심 여행이면,
                단순히 가까운 음식점보다
                평점 신뢰도와 제주에서 먹을 가치가 높은 음식점을
                더 적극적으로 평가한다.

                일반 여행이면
                지나치게 먼 음식점은 우선순위를 낮춘다.

                평가 기준:
                1. 음식점 평점과 리뷰 수의 신뢰도
                2. 사용자 음식 카테고리
                3. 현재 위치에서의 접근성
                4. 메뉴 구성
                5. 제주 여행에서 방문할 가치
                6. 제주 로컬 음식 특성
                7. 후보 간 메뉴 다양성

                흑돼지 음식점만 여러 개,
                갈치 음식점만 여러 개처럼
                비슷한 메뉴가 과도하게 중복되지 않도록 한다.

                최대 %d개를 선택한다.

                candidates:
                %s

                반드시 아래 JSON 형식만 반환한다.

                {
                  "recommendations": [
                    {
                      "restaurantId": 123,
                      "aiScore": 92,
                      "reason": "제주 향토 메뉴와 높은 리뷰 신뢰도를 갖춘 음식점입니다."
                    }
                  ]
                }
                """
                        .formatted(
                                request.resolvedFoodPreferences(),
                                request.resolvedPreferences(),
                                request.foodFocused(),
                                limit,
                                candidateJsonString
                        );

        String response =
                bedrockClient
                        .converse(
                                prompt
                        );

        return parseAiResponse(
                response,
                candidates,
                limit
        );
    }

    private List<AiDecision> parseAiResponse(
            String response,
            List<ScoredRestaurant> candidates,
            int limit
    ) throws JacksonException {

        String json =
                extractJson(
                        response
                );

        JsonNode root =
                jsonMapper
                        .readTree(
                                json
                        );

        JsonNode recommendations =
                root.path(
                        "recommendations"
                );

        if (!recommendations.isArray()) {

            throw new IllegalStateException(
                    "Bedrock 음식점 추천 응답 형식이 올바르지 않습니다."
            );
        }

        Set<Long> validIds =
                candidates
                        .stream()
                        .map(
                                item ->
                                        item.restaurant().id()
                        )
                        .collect(
                                Collectors.toSet()
                        );

        Set<Long> used =
                new HashSet<>();

        List<AiDecision> decisions =
                new ArrayList<>();

        for (JsonNode item : recommendations) {

            if (decisions.size() >= limit) {
                break;
            }

            long restaurantId =
                    item.path(
                            "restaurantId"
                    ).asLong(-1);

            if (!validIds.contains(restaurantId)
                    ||
                    !used.add(restaurantId)) {

                continue;
            }

            double aiScore =
                    clamp(
                            item.path(
                                    "aiScore"
                            ).asDouble(50.0),
                            0.0,
                            100.0
                    );

            String reason =
                    item.path(
                            "reason"
                    ).asText("");

            decisions.add(
                    new AiDecision(
                            restaurantId,
                            aiScore,
                            reason
                    )
            );
        }

        return decisions;
    }

    private List<RestaurantRecommendation>
    buildFinalRecommendations(
            List<ScoredRestaurant> candidates,
            List<AiDecision> decisions,
            Map<Long, List<RestaurantMenuData>> menuMap,
            Set<FoodPreference> requestedFoodPreferences,
            int limit
    ) {

        Map<Long, ScoredRestaurant> byId =
                new HashMap<>();

        for (ScoredRestaurant candidate : candidates) {

            byId.put(
                    candidate.restaurant().id(),
                    candidate
            );
        }

        List<FinalRestaurant> finalRestaurants =
                new ArrayList<>();

        Set<Long> selected =
                new HashSet<>();

        for (AiDecision decision : decisions) {

            ScoredRestaurant candidate =
                    byId.get(
                            decision.restaurantId()
                    );

            if (candidate == null) {
                continue;
            }

            double aiNormalized =
                    decision.aiScore()
                            / 100.0;

            double finalScore =
                    candidate.baseScore()
                            * BASE_FINAL_WEIGHT

                            +

                            aiNormalized
                                    * AI_FINAL_WEIGHT;

            finalRestaurants.add(
                    new FinalRestaurant(
                            candidate,
                            decision.aiScore(),
                            decision.reason(),
                            finalScore
                    )
            );

            selected.add(
                    decision.restaurantId()
            );
        }

        /*
         * Bedrock이 limit보다 적게 반환하거나
         * 호출 실패하면 baseScore로 보충.
         */
        candidates
                .stream()

                .filter(
                        candidate ->
                                !selected.contains(
                                        candidate.restaurant().id()
                                )
                )

                .sorted(
                        Comparator
                                .comparingDouble(
                                        ScoredRestaurant::baseScore
                                )
                                .reversed()
                )

                .limit(
                        Math.max(
                                0,
                                limit
                                        -
                                        finalRestaurants.size()
                        )
                )

                .forEach(
                        candidate ->
                                finalRestaurants.add(
                                        new FinalRestaurant(
                                                candidate,
                                                null,
                                                null,
                                                candidate.baseScore()
                                        )
                                )
                );

        return finalRestaurants
                .stream()

                .sorted(
                        Comparator
                                .comparingDouble(
                                        FinalRestaurant::finalScore
                                )
                                .reversed()
                )

                .limit(limit)

                .map(
                        item ->
                                toRecommendation(
                                        item,
                                        requestedFoodPreferences,
                                        menuMap.getOrDefault(
                                                item.scored()
                                                        .restaurant()
                                                        .id(),
                                                List.of()
                                        )
                                )
                )

                .toList();
    }

    private RestaurantRecommendation toRecommendation(
            FinalRestaurant finalRestaurant,
            Set<FoodPreference> requestedFoodPreferences,
            List<RestaurantMenuData> menus
    ) {

        ScoredRestaurant scored =
                finalRestaurant.scored();

        RestaurantData restaurant =
                scored.restaurant();

        return new RestaurantRecommendation(

                restaurant.id(),

                restaurant.kakaoPlaceId(),

                restaurant.restaurantName(),

                restaurant.category(),

                resolveMatchedPreferences(
                        restaurant.category(),
                        requestedFoodPreferences
                ),

                restaurant.phone(),

                restaurant.address(),

                restaurant.roadAddress(),

                restaurant.latitude(),

                restaurant.longitude(),

                restaurant.placeUrl(),

                restaurant.rating(),

                restaurant.reviewCount(),

                restaurant.businessHours(),

                restaurant.summary(),

                restaurant.tags(),

                restaurant.facilities(),

                menus,

                scored.distanceKm(),

                scored.estimatedDriveMinutes(),

                round(
                        scored.bayesianRating(),
                        2
                ),

                round(
                        scored.ratingScore()
                                * 100.0,
                        2
                ),

                round(
                        scored.distanceScore()
                                * 100.0,
                        2
                ),

                round(
                        scored.localScore()
                                * 100.0,
                        2
                ),

                round(
                        scored.baseScore()
                                * 100.0,
                        2
                ),

                finalRestaurant.aiScore(),

                round(
                        finalRestaurant.finalScore()
                                * 100.0,
                        2
                ),

                finalRestaurant.reason()
        );
    }

    private Set<FoodPreference>
    resolveMatchedPreferences(
            String category,
            Set<FoodPreference> requestedPreferences
    ) {

        if (requestedPreferences != null
                && !requestedPreferences.isEmpty()) {

            return requestedPreferences
                    .stream()
                    .filter(
                            preference ->
                                    preference
                                            .matchesCategory(
                                                    category
                                            )
                    )
                    .collect(
                            Collectors.toUnmodifiableSet()
                    );
        }

        return Arrays
                .stream(
                        FoodPreference.values()
                )
                .filter(
                        preference ->
                                preference
                                        .matchesCategory(
                                                category
                                        )
                )
                .collect(
                        Collectors.toUnmodifiableSet()
                );
    }

    private int calculateEstimatedDriveMinutes(
            double distanceKm
    ) {

        return (int) Math.ceil(
                (
                        distanceKm
                                /
                                ESTIMATED_DRIVE_SPEED_KMH
                )
                        *
                        60.0
        );
    }

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
                        *
                        Math.sin(
                                latDistance / 2.0
                        )

                        +

                        Math.cos(
                                Math.toRadians(lat1)
                        )
                                *
                                Math.cos(
                                        Math.toRadians(lat2)
                                )
                                *
                                Math.sin(
                                        lonDistance / 2.0
                                )
                                *
                                Math.sin(
                                        lonDistance / 2.0
                                );

        double c =
                2.0
                        *
                        Math.atan2(
                                Math.sqrt(a),
                                Math.sqrt(
                                        1.0 - a
                                )
                        );

        return earthRadiusKm * c;
    }

    private boolean containsAny(
            String text,
            List<String> keywords
    ) {

        return keywords
                .stream()
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

    private String truncate(
            String value,
            int maxLength
    ) {

        if (value == null) {
            return "";
        }

        if (value.length() <= maxLength) {
            return value;
        }

        return value.substring(
                0,
                maxLength
        );
    }

    private String extractJson(
            String value
    ) {

        if (value == null) {

            throw new IllegalStateException(
                    "Bedrock 응답이 없습니다."
            );
        }

        String trimmed =
                value.trim();

        int start =
                trimmed.indexOf(
                        '{'
                );

        int end =
                trimmed.lastIndexOf(
                        '}'
                );

        if (start < 0
                || end < start) {

            throw new IllegalStateException(
                    "Bedrock 응답에서 JSON을 찾을 수 없습니다."
            );
        }

        return trimmed.substring(
                start,
                end + 1
        );
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

    private record ScoredRestaurant(

            RestaurantData restaurant,

            double distanceKm,

            int estimatedDriveMinutes,

            double bayesianRating,

            double ratingScore,

            double distanceScore,

            double localScore,

            double baseScore

    ) {
    }

    private record CandidateWindow(

            int windowMinutes,

            List<ScoredRestaurant> candidates

    ) {
    }

    private record AiDecision(

            long restaurantId,

            double aiScore,

            String reason

    ) {
    }

    private record FinalRestaurant(

            ScoredRestaurant scored,

            Double aiScore,

            String reason,

            double finalScore

    ) {
    }
}