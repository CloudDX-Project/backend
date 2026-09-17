package com.travel.cafe;

import com.travel.cafe.CafeCandidateService.CafeCandidatePool;
import com.travel.cafe.CafeCandidateService.CafeScoredCandidate;
import com.travel.cafe.data.CafeData;
import com.travel.cafe.data.CafeMenuData;
import com.travel.cafe.dto.CafeMenuResponse;
import com.travel.cafe.dto.CafeRecommendRequest;
import com.travel.cafe.dto.CafeRecommendResponse;
import com.travel.cafe.dto.CafeRecommendation;
import com.travel.cafe.repository.CafeRepository;
import com.travel.external.bedrock.BedrockClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CafeRecommendationService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    CafeRecommendationService.class
            );

    /*
     * Nova 2 Lite에 너무 많은 후보를 넘기지 않는다.
     */
    private static final int
            MIN_AI_CANDIDATE_POOL =
            20;

    private static final int
            MAX_AI_CANDIDATE_POOL =
            30;

    /*
     * Backend 자체 점수 + AI 점수
     */
    private static final double
            BASE_FINAL_WEIGHT =
            0.45;

    private static final double
            AI_FINAL_WEIGHT =
            0.55;

    private final CafeCandidateService candidateService;

    private final CafeRepository cafeRepository;

    private final BedrockClient bedrockClient;

    private final JsonMapper jsonMapper;

    public CafeRecommendationService(

            CafeCandidateService candidateService,

            CafeRepository cafeRepository,

            BedrockClient bedrockClient,

            JsonMapper jsonMapper

    ) {

        this.candidateService =
                candidateService;

        this.cafeRepository =
                cafeRepository;

        this.bedrockClient =
                bedrockClient;

        this.jsonMapper =
                jsonMapper;
    }

    @Transactional(readOnly = true)
    public CafeRecommendResponse recommend(
            CafeRecommendRequest request
    ) {

        int limit =
                request.resolvedLimit();

        int candidatePoolSize =
                Math.min(
                        MAX_AI_CANDIDATE_POOL,
                        Math.max(
                                MIN_AI_CANDIDATE_POOL,
                                limit * 3
                        )
                );

        String candidateCacheKey =
                buildCandidateCacheKey(
                        request,
                        candidatePoolSize
                );

        /*
         * 여기 결과가 Redis에 캐싱된다.
         */
        CafeCandidatePool pool =
                candidateService
                        .getCandidatePool(
                                candidateCacheKey,
                                request.originLatitude(),
                                request.originLongitude(),
                                request.foodFocused(),
                                candidatePoolSize
                        );

        List<CafeScoredCandidate> candidates =
                pool.candidates();

        if (candidates.isEmpty()) {

            return new CafeRecommendResponse(
                    request.originLatitude(),
                    request.originLongitude(),
                    request.foodFocused(),
                    limit,
                    pool.searchWindowMinutes(),
                    "STRAIGHT_LINE_ESTIMATE",
                    0,
                    List.of()
            );
        }

        List<Long> cafeIds =
                candidates.stream()
                        .map(
                                candidate ->
                                        candidate.cafe().id()
                        )
                        .toList();

        Map<Long, List<CafeMenuData>> menuMap =
                cafeRepository.findMenusByCafeIds(
                        cafeIds
                );

        List<AiDecision> aiDecisions;

        try {

            aiDecisions =
                    rerankWithBedrock(
                            request,
                            candidates,
                            menuMap,
                            limit
                    );

        } catch (Exception e) {

            log.warn(
                    "카페 Bedrock reranking 실패. "
                            + "자체 추천 점수로 fallback 합니다.",
                    e
            );

            aiDecisions =
                    List.of();
        }

        List<CafeRecommendation> recommendations =
                buildFinalRecommendations(
                        candidates,
                        aiDecisions,
                        menuMap,
                        limit
                );

        return new CafeRecommendResponse(
                request.originLatitude(),
                request.originLongitude(),
                request.foodFocused(),
                limit,
                pool.searchWindowMinutes(),
                "STRAIGHT_LINE_ESTIMATE",
                candidates.size(),
                recommendations
        );
    }

    /*
     * 같은 위치 + 같은 FOOD 여부 + 같은 후보크기라면
     * Redis 후보 pool 재사용.
     *
     * v1은 추천 로직 버전.
     * 점수 정책을 크게 바꾸면 v2로 올리면 된다.
     */
    private String buildCandidateCacheKey(

            CafeRecommendRequest request,

            int candidatePoolSize

    ) {

        return String.format(
                Locale.ROOT,
                "v1:%.5f:%.5f:%s:%d",
                request.originLatitude(),
                request.originLongitude(),
                request.foodFocused(),
                candidatePoolSize
        );
    }

    private List<AiDecision> rerankWithBedrock(

            CafeRecommendRequest request,

            List<CafeScoredCandidate> candidates,

            Map<Long, List<CafeMenuData>> menuMap,

            int limit

    ) throws JacksonException {

        List<Map<String, Object>> candidateJson =
                new ArrayList<>();

        for (CafeScoredCandidate scored : candidates) {

            CafeData cafe =
                    scored.cafe();

            Map<String, Object> item =
                    new LinkedHashMap<>();

            item.put(
                    "cafeId",
                    cafe.id()
            );

            item.put(
                    "name",
                    cafe.cafeName()
            );

            item.put(
                    "category",
                    cafe.category()
            );

            item.put(
                    "rating",
                    cafe.rating()
            );

            item.put(
                    "reviewCount",
                    cafe.reviewCount()
            );

            item.put(
                    "bayesianRating",
                    round(
                            scored.bayesianRating(),
                            2
                    )
            );

            item.put(
                    "distanceKm",
                    scored.distanceKm()
            );

            item.put(
                    "estimatedDriveMinutes",
                    scored.estimatedDriveMinutes()
            );

            item.put(
                    "summary",
                    truncate(
                            cafe.summary(),
                            140
                    )
            );

            item.put(
                    "tags",
                    truncate(
                            cafe.tags(),
                            160
                    )
            );

            item.put(
                    "facilities",
                    truncate(
                            cafe.facilities(),
                            120
                    )
            );

            item.put(
                    "baseScore",
                    round(
                            scored.baseScore()
                                    * 100.0,
                            2
                    )
            );

            /*
             * 토큰 절약을 위해 메뉴 최대 5개만.
             *
             * Repository에서 추천메뉴가 먼저 정렬됨.
             */
            List<Map<String, Object>> menus =
                    menuMap
                            .getOrDefault(
                                    cafe.id(),
                                    List.of()
                            )
                            .stream()
                            .limit(5)
                            .map(
                                    menu -> {

                                        Map<String, Object> menuJson =
                                                new LinkedHashMap<>();

                                        menuJson.put(
                                                "name",
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
                                                "tags",
                                                truncate(
                                                        menu.menuTags(),
                                                        80
                                                )
                                        );

                                        return menuJson;
                                    }
                            )
                            .toList();

            item.put(
                    "menus",
                    menus
            );

            candidateJson.add(
                    item
            );
        }

        String candidatesJson =
                jsonMapper.writeValueAsString(
                        candidateJson
                );

        String prompt =
                """
                너는 제주 여행 카페 추천 reranker다.

                아래 candidates는 실제 DB에 존재하는 카페다.

                반드시 candidates에 존재하는 cafeId만 사용한다.
                새로운 카페나 cafeId를 절대로 생성하지 않는다.

                여행 테마:
                %s

                FOOD 중심 여행 여부:
                %s

                추천 기준:
                1. 평점과 리뷰 수의 신뢰도
                2. 현재 위치에서의 접근성
                3. 메뉴 구성과 대표 메뉴
                4. 제주에서 방문할 가치
                5. 제주 특색 메뉴 또는 디저트
                6. 오션뷰, 정원, 로스터리 등 카페 경험
                7. 여행 테마와의 조화

                FOOD 중심 여행이면 단순히 가까운 카페보다
                음식/디저트 품질과 방문 가치가 높은 카페를
                더 적극적으로 평가한다.

                NATURE 또는 HEALING 테마가 있다면
                자연경관, 바다 전망, 정원, 휴식 분위기 등
                후보 데이터에 실제로 존재하는 특징을 고려한다.

                후보 데이터에 없는 특징을 만들어내지 않는다.

                최대 %d개를 선택한다.

                candidates:
                %s

                반드시 JSON만 반환한다.

                {
                  "recommendations": [
                    {
                      "cafeId": 123,
                      "aiScore": 92,
                      "reason": "바다 전망과 높은 리뷰 신뢰도를 함께 갖춘 카페입니다."
                    }
                  ]
                }
                """
                        .formatted(
                                request.resolvedPreferences(),
                                request.foodFocused(),
                                limit,
                                candidatesJson
                        );

        String response =
                bedrockClient.converse(
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

            List<CafeScoredCandidate> candidates,

            int limit

    ) throws JacksonException {

        String json =
                extractJson(
                        response
                );

        JsonNode root =
                jsonMapper.readTree(
                        json
                );

        JsonNode recommendations =
                root.path(
                        "recommendations"
                );

        if (!recommendations.isArray()) {

            throw new IllegalStateException(
                    "Bedrock 카페 추천 응답 형식이 올바르지 않습니다."
            );
        }

        Set<Long> validIds =
                new HashSet<>();

        for (CafeScoredCandidate candidate : candidates) {

            validIds.add(
                    candidate.cafe().id()
            );
        }

        Set<Long> used =
                new HashSet<>();

        List<AiDecision> decisions =
                new ArrayList<>();

        for (JsonNode item : recommendations) {

            if (decisions.size() >= limit) {
                break;
            }

            long cafeId =
                    item.path(
                            "cafeId"
                    ).asLong(-1L);

            if (!validIds.contains(cafeId)
                    ||
                    !used.add(cafeId)) {

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
                            cafeId,
                            aiScore,
                            reason
                    )
            );
        }

        return decisions;
    }

    private List<CafeRecommendation>
    buildFinalRecommendations(

            List<CafeScoredCandidate> candidates,

            List<AiDecision> decisions,

            Map<Long, List<CafeMenuData>> menuMap,

            int limit

    ) {

        /*
         * Bedrock 장애 시
         * Backend 자체 점수만 사용.
         */
        if (decisions == null
                || decisions.isEmpty()) {

            return candidates.stream()
                    .sorted(
                            Comparator
                                    .comparingDouble(
                                            CafeScoredCandidate::baseScore
                                    )
                                    .reversed()
                    )
                    .limit(limit)
                    .map(
                            candidate ->
                                    toRecommendation(
                                            candidate,
                                            null,
                                            "평점 신뢰도와 접근성, 카페 특성을 기준으로 선정되었습니다.",
                                            candidate.baseScore(),
                                            menuMap.getOrDefault(
                                                    candidate.cafe().id(),
                                                    List.of()
                                            )
                                    )
                    )
                    .toList();
        }

        Map<Long, CafeScoredCandidate> candidateMap =
                new HashMap<>();

        for (CafeScoredCandidate candidate : candidates) {

            candidateMap.put(
                    candidate.cafe().id(),
                    candidate
            );
        }

        List<FinalCafe> result =
                new ArrayList<>();

        Set<Long> selected =
                new HashSet<>();

        for (AiDecision decision : decisions) {

            CafeScoredCandidate candidate =
                    candidateMap.get(
                            decision.cafeId()
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

            result.add(
                    new FinalCafe(
                            candidate,
                            decision.aiScore(),
                            decision.reason(),
                            finalScore
                    )
            );

            selected.add(
                    decision.cafeId()
            );
        }

        /*
         * Bedrock이 limit보다 적게 반환한 경우
         * 자체점수 후보로 부족분 보충.
         */
        candidates.stream()
                .filter(
                        candidate ->
                                !selected.contains(
                                        candidate.cafe().id()
                                )
                )
                .sorted(
                        Comparator
                                .comparingDouble(
                                        CafeScoredCandidate::baseScore
                                )
                                .reversed()
                )
                .limit(
                        Math.max(
                                0,
                                limit - result.size()
                        )
                )
                .forEach(
                        candidate -> {

                            /*
                             * AI 평가가 없으므로
                             * 중립 50점으로 결합.
                             */
                            double fallbackFinalScore =
                                    candidate.baseScore()
                                            * BASE_FINAL_WEIGHT

                                            +

                                            0.50
                                                    * AI_FINAL_WEIGHT;

                            result.add(
                                    new FinalCafe(
                                            candidate,
                                            null,
                                            "자체 추천 점수를 기준으로 보충된 카페입니다.",
                                            fallbackFinalScore
                                    )
                            );
                        }
                );

        return result.stream()
                .sorted(
                        Comparator
                                .comparingDouble(
                                        FinalCafe::finalScore
                                )
                                .reversed()
                )
                .limit(limit)
                .map(
                        finalCafe ->
                                toRecommendation(
                                        finalCafe.candidate(),
                                        finalCafe.aiScore(),
                                        finalCafe.reason(),
                                        finalCafe.finalScore(),
                                        menuMap.getOrDefault(
                                                finalCafe
                                                        .candidate()
                                                        .cafe()
                                                        .id(),
                                                List.of()
                                        )
                                )
                )
                .toList();
    }

    private CafeRecommendation toRecommendation(

            CafeScoredCandidate candidate,

            Double aiScore,

            String reason,

            double finalScore,

            List<CafeMenuData> menus

    ) {

        CafeData cafe =
                candidate.cafe();

        return new CafeRecommendation(
                cafe.id(),
                cafe.kakaoPlaceId(),
                cafe.cafeName(),
                cafe.category(),
                cafe.phone(),
                cafe.address(),
                cafe.roadAddress(),
                cafe.latitude(),
                cafe.longitude(),
                cafe.placeUrl(),
                cafe.rating(),
                cafe.reviewCount(),
                cafe.businessHours(),
                cafe.summary(),
                cafe.tags(),
                cafe.facilities(),
                menus.stream()
                        .map(this::toMenuResponse)
                        .toList(),
                candidate.distanceKm(),
                candidate.estimatedDriveMinutes(),
                round(
                        candidate.bayesianRating(),
                        2
                ),
                round(
                        candidate.ratingScore()
                                * 100.0,
                        2
                ),
                round(
                        candidate.distanceScore()
                                * 100.0,
                        2
                ),
                round(
                        candidate.experienceScore()
                                * 100.0,
                        2
                ),
                round(
                        candidate.baseScore()
                                * 100.0,
                        2
                ),
                aiScore,
                round(
                        finalScore
                                * 100.0,
                        2
                ),
                reason
        );
    }

    private CafeMenuResponse toMenuResponse(
            CafeMenuData menu
    ) {

        return new CafeMenuResponse(
                menu.id(),
                menu.menuName(),
                menu.description(),
                menu.imageUrl(),
                menu.currentPrice(),
                menu.recommended(),
                menu.menuTags()
        );
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
            String response
    ) {

        if (response == null
                || response.isBlank()) {

            throw new IllegalStateException(
                    "Bedrock 응답이 없습니다."
            );
        }

        String trimmed =
                response.trim();

        int start =
                trimmed.indexOf('{');

        int end =
                trimmed.lastIndexOf('}');

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

    private record AiDecision(

            long cafeId,

            double aiScore,

            String reason

    ) {
    }

    private record FinalCafe(

            CafeScoredCandidate candidate,

            Double aiScore,

            String reason,

            double finalScore

    ) {
    }
}