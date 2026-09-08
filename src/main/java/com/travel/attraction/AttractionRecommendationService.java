package com.travel.attraction;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.travel.attraction.data.TouristAttractionData;
import com.travel.attraction.dto.AttractionRecommendRequest;
import com.travel.attraction.dto.AttractionRecommendResponse;
import com.travel.attraction.dto.AttractionRecommendation;
import com.travel.attraction.repository.TouristAttractionRepository;
import com.travel.external.bedrock.BedrockClient;
import com.travel.trip.entity.TripPace;
import com.travel.trip.entity.TripPreference;
import com.travel.weather.WeatherCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
public class AttractionRecommendationService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    AttractionRecommendationService.class
            );

    /*
     * 실제 길찾기 API 연동 전 임시 추정값.
     */
    private static final double ESTIMATED_DRIVE_SPEED_KMH =
            45.0;

    private static final int PRIMARY_WINDOW_MINUTES =
            30;

    private static final int FALLBACK_WINDOW_MINUTES =
            45;

    /*
     * Bedrock에는 전체 1302개를 보내지 않고
     * 백엔드에서 30개 정도로 먼저 압축.
     */
    private static final int MIN_AI_CANDIDATE_POOL =
            30;

    private static final int MAX_AI_CANDIDATE_POOL =
            50;

    /*
     * 1차 정량 점수
     *
     * 위치 35%
     * 사용자 선호 40%
     * 날씨 15%
     * 여행 pace 10%
     */
    private static final double DISTANCE_WEIGHT =
            0.35;

    private static final double PREFERENCE_WEIGHT =
            0.40;

    private static final double WEATHER_WEIGHT =
            0.15;

    private static final double PACE_WEIGHT =
            0.10;

    /*
     * 최종 점수
     *
     * 백엔드 정량점수 45%
     * Bedrock 개인화점수 55%
     */
    private static final double BASE_FINAL_WEIGHT =
            0.45;

    private static final double AI_FINAL_WEIGHT =
            0.55;

    private final TouristAttractionRepository attractionRepository;

    private final BedrockClient bedrockClient;

    private final JsonMapper jsonMapper;

    public AttractionRecommendationService(
            TouristAttractionRepository attractionRepository,
            BedrockClient bedrockClient,
            JsonMapper jsonMapper
    ) {
        this.attractionRepository = attractionRepository;
        this.bedrockClient = bedrockClient;
        this.jsonMapper = jsonMapper;
    }


    @Transactional(readOnly = true)
    public AttractionRecommendResponse recommend(
            AttractionRecommendRequest request
    ) {

        int limit =
                request.resolvedLimit();

        /*
         * Bedrock 후보 pool.
         *
         * limit=12라면 기본 36개.
         */
        int candidatePoolSize =
                Math.min(
                        MAX_AI_CANDIDATE_POOL,
                        Math.max(
                                MIN_AI_CANDIDATE_POOL,
                                limit * 3
                        )
                );

        List<TouristAttractionData> attractions =
                attractionRepository
                        .findAllRecommendable();

        if (attractions.isEmpty()) {

            return new AttractionRecommendResponse(
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
         * 모든 관광지의 1차 점수 계산
         */
        List<ScoredAttraction> scored =
                attractions.stream()
                        .map(
                                attraction ->
                                        score(
                                                attraction,
                                                request
                                        )
                        )
                        .toList();


        /*
         * 30분 → 45분 → 전체 제주 fallback
         */
        CandidateWindow window =
                chooseCandidateWindow(
                        scored,
                        candidatePoolSize
                );


        /*
         * 1차 정량점수 상위 30~50개만
         * Bedrock으로 보냄.
         */
        List<ScoredAttraction> aiCandidates =
                window.candidates()
                        .stream()
                        .sorted(
                                Comparator
                                        .comparingDouble(
                                                ScoredAttraction::baseScore
                                        )
                                        .reversed()
                        )
                        .limit(candidatePoolSize)
                        .toList();


        /*
         * Bedrock reranking.
         *
         * 실패해도 API 전체를 실패시키지 않고
         * 정량점수로 fallback.
         */
        List<AiDecision> aiDecisions;

        try {

            aiDecisions =
                    rerankWithBedrock(
                            request,
                            aiCandidates,
                            limit
                    );

        } catch (Exception e) {

            log.warn(
                    "관광지 Bedrock reranking 실패. "
                            + "정량 추천으로 fallback 합니다.",
                    e
            );

            aiDecisions =
                    List.of();
        }


        List<AttractionRecommendation> recommendations =
                buildFinalRecommendations(
                        aiCandidates,
                        aiDecisions,
                        limit
                );


        return new AttractionRecommendResponse(
                request.latitude(),
                request.longitude(),

                limit,

                window.windowMinutes(),

                "STRAIGHT_LINE_ESTIMATE",

                aiCandidates.size(),

                recommendations
        );
    }


    private ScoredAttraction score(
            TouristAttractionData attraction,
            AttractionRecommendRequest request
    ) {

        double distanceKm =
                calculateDistanceKm(
                        request.latitude(),
                        request.longitude(),
                        attraction.latitude(),
                        attraction.longitude()
                );


        int estimatedDriveMinutes =
                calculateEstimatedDriveMinutes(
                        distanceKm
                );


        double distanceScore =
                calculateDistanceScore(
                        estimatedDriveMinutes
                );


        double preferenceScore =
                calculatePreferenceScore(
                        attraction,
                        request.resolvedPreferences()
                );


        double weatherScore =
                calculateWeatherScore(
                        attraction,
                        request.resolvedWeatherCondition()
                );


        double paceScore =
                calculatePaceScore(
                        attraction,
                        request.resolvedPace()
                );


        double baseScore =

                distanceScore
                        * DISTANCE_WEIGHT

                        +

                        preferenceScore
                                * PREFERENCE_WEIGHT

                        +

                        weatherScore
                                * WEATHER_WEIGHT

                        +

                        paceScore
                                * PACE_WEIGHT;


        return new ScoredAttraction(
                attraction,

                round(
                        distanceKm,
                        2
                ),

                estimatedDriveMinutes,

                preferenceScore,

                weatherScore,

                paceScore,

                clamp(
                        baseScore,
                        0.0,
                        1.0
                )
        );
    }


    /**
     * 관광지 주변 30분권 우선.
     *
     * 후보가 부족하면 45분.
     *
     * 그래도 부족하면 제주 전체.
     */
    private CandidateWindow chooseCandidateWindow(
            List<ScoredAttraction> scored,
            int targetCount
    ) {

        List<ScoredAttraction> within30 =
                scored.stream()
                        .filter(
                                item ->
                                        item.estimatedDriveMinutes()
                                                <= PRIMARY_WINDOW_MINUTES
                        )
                        .toList();


        if (within30.size() >= targetCount) {

            return new CandidateWindow(
                    PRIMARY_WINDOW_MINUTES,
                    within30
            );
        }


        List<ScoredAttraction> within45 =
                scored.stream()
                        .filter(
                                item ->
                                        item.estimatedDriveMinutes()
                                                <= FALLBACK_WINDOW_MINUTES
                        )
                        .toList();


        if (within45.size() >= targetCount) {

            return new CandidateWindow(
                    FALLBACK_WINDOW_MINUTES,
                    within45
            );
        }


        return new CandidateWindow(
                0,
                scored
        );
    }


    /**
     * 사용자 선호도와 VisitJeju tag/alltag 비교.
     */
    private double calculatePreferenceScore(
            TouristAttractionData attraction,
            List<TripPreference> preferences
    ) {

        if (preferences == null
                || preferences.isEmpty()) {

            return 0.5;
        }


        String text =
                attractionText(
                        attraction
                );


        int matched =
                0;


        for (TripPreference preference : preferences) {

            List<String> keywords =
                    preferenceKeywords(
                            preference
                    );


            boolean match =
                    keywords.stream()
                            .anyMatch(
                                    text::contains
                            );


            if (match) {
                matched++;
            }
        }


        if (matched == 0) {
            return 0.25;
        }


        double ratio =
                matched
                        / (double) preferences.size();


        return clamp(
                0.25
                        + ratio * 0.75,
                0.0,
                1.0
        );
    }


    private List<String> preferenceKeywords(
            TripPreference preference
    ) {

        return switch (preference) {

            case NATURE ->
                    List.of(
                            "오름",
                            "해변",
                            "바다",
                            "숲",
                            "폭포",
                            "자연",
                            "산",
                            "동굴",
                            "정원",
                            "생태"
                    );

            case SIGHTSEEING ->
                    List.of(
                            "경관",
                            "포토",
                            "전망",
                            "일출",
                            "일몰",
                            "야경",
                            "명소",
                            "유네스코",
                            "관광"
                    );

            case HISTORY ->
                    List.of(
                            "역사",
                            "유적",
                            "문화재",
                            "민속",
                            "4.3",
                            "박물관",
                            "기념관"
                    );

            case ACTIVITY ->
                    List.of(
                            "체험",
                            "레저",
                            "스쿠버",
                            "서핑",
                            "승마",
                            "카약",
                            "요트",
                            "낚시",
                            "등산"
                    );

            case HEALING ->
                    List.of(
                            "힐링",
                            "산책",
                            "숲",
                            "휴양",
                            "정원",
                            "공원",
                            "명상",
                            "온천"
                    );

            case FOOD ->
                    List.of(
                            "시장",
                            "먹거리",
                            "음식",
                            "전통시장"
                    );

            case CAFE ->
                    List.of(
                            "카페",
                            "커피",
                            "디저트",
                            "베이커리"
                    );
        };
    }


    /**
     * 날씨 점수.
     *
     * 비/눈일 때 실내 관광지를 우대.
     */
    private double calculateWeatherScore(
            TouristAttractionData attraction,
            WeatherCondition weather
    ) {

        String text =
                attractionText(
                        attraction
                );


        boolean indoor =
                containsAny(
                        text,
                        List.of(
                                "실내",
                                "박물관",
                                "미술관",
                                "전시",
                                "체험관",
                                "아쿠아리움",
                                "공연",
                                "기념관"
                        )
                );


        boolean outdoor =
                containsAny(
                        text,
                        List.of(
                                "실외",
                                "오름",
                                "해변",
                                "바다",
                                "산",
                                "등산",
                                "올레",
                                "산책로",
                                "폭포"
                        )
                );


        return switch (weather) {

            case RAIN, SNOW -> {

                if (indoor) {
                    yield 1.0;
                }

                if (outdoor) {
                    yield 0.30;
                }

                yield 0.60;
            }


            case SUNNY -> {

                if (outdoor) {
                    yield 1.0;
                }

                if (indoor) {
                    yield 0.80;
                }

                yield 0.85;
            }


            case CLOUDY -> {

                if (indoor) {
                    yield 0.90;
                }

                if (outdoor) {
                    yield 0.85;
                }

                yield 0.80;
            }


            case UNKNOWN ->
                    0.75;
        };
    }


    private double calculatePaceScore(
            TouristAttractionData attraction,
            TripPace pace
    ) {

        String text =
                attractionText(
                        attraction
                );


        boolean active =
                containsAny(
                        text,
                        List.of(
                                "등산",
                                "스쿠버",
                                "서핑",
                                "레저",
                                "승마",
                                "카약",
                                "오름",
                                "트레킹"
                        )
                );


        boolean relaxed =
                containsAny(
                        text,
                        List.of(
                                "산책",
                                "공원",
                                "정원",
                                "휴양",
                                "힐링",
                                "1시간 미만"
                        )
                );


        return switch (pace) {

            case RELAXED -> {

                if (relaxed) {
                    yield 1.0;
                }

                if (active) {
                    yield 0.45;
                }

                yield 0.75;
            }


            case ACTIVE -> {

                if (active) {
                    yield 1.0;
                }

                if (relaxed) {
                    yield 0.70;
                }

                yield 0.80;
            }


            case BALANCED ->
                    0.85;
        };
    }


    private double calculateDistanceScore(
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


    private int calculateEstimatedDriveMinutes(
            double distanceKm
    ) {

        return (int) Math.ceil(
                (
                        distanceKm
                                / ESTIMATED_DRIVE_SPEED_KMH
                )
                        * 60.0
        );
    }


    /**
     * Haversine.
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
                        * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(
                                1.0 - a
                        )
                );


        return earthRadiusKm * c;
    }


    /**
     * Bedrock에 상위 후보만 전달.
     */
    private List<AiDecision> rerankWithBedrock(
            AttractionRecommendRequest request,
            List<ScoredAttraction> candidates,
            int limit
    ) throws JacksonException {

        List<Map<String, Object>> candidateJson =
                new ArrayList<>();


        for (ScoredAttraction candidate : candidates) {

            TouristAttractionData attraction =
                    candidate.attraction();


            Map<String, Object> item =
                    new LinkedHashMap<>();


            item.put(
                    "providerId",
                    attraction.providerId()
            );

            item.put(
                    "name",
                    attraction.name()
            );

            item.put(
                    "region",
                    attraction.region1Name()
                            + " "
                            + attraction.region2Name()
            );

            item.put(
                    "distanceKm",
                    candidate.distanceKm()
            );

            item.put(
                    "estimatedDriveMinutes",
                    candidate.estimatedDriveMinutes()
            );

            item.put(
                    "tags",
                    truncate(
                            attraction.tags(),
                            150
                    )
            );

            item.put(
                    "allTags",
                    truncate(
                            attraction.allTags(),
                            250
                    )
            );

            item.put(
                    "introduction",
                    truncate(
                            attraction.introduction(),
                            180
                    )
            );

            item.put(
                    "baseScore",
                    round(
                            candidate.baseScore()
                                    * 100.0,
                            2
                    )
            );


            candidateJson.add(
                    item
            );
        }


        String candidatesString =
                jsonMapper
                        .writeValueAsString(
                                candidateJson
                        );


        String prompt =
                """
                너는 제주 여행 관광지 추천 reranker다.

                아래 후보들은 실제 VisitJeju DB에 존재하는 관광지다.

                반드시 candidates에 존재하는 providerId만 사용한다.
                새로운 장소나 providerId를 절대로 생성하지 않는다.

                추천 기준:
                1. 사용자의 여행 선호도
                2. 관광지까지의 접근성
                3. 현재 날씨
                4. 여행 pace
                5. 관광지 태그와 소개
                6. 실제 여행 중 방문할 가치

                비 또는 눈이면 실내 관광지를 우선 고려한다.
                맑은 날이면 자연/야외 관광지를 적극 고려한다.

                단순 업체명, 교통회사, 일반 사업체보다
                실제 관광 경험이 가능한 장소를 우선한다.

                비슷한 장소만 과도하게 중복 추천하지 않는다.

                recommendation reason은 한국어 한 문장으로 짧게 작성한다.

                최대 %d개를 선택한다.

                사용자 정보:
                latitude=%s
                longitude=%s
                preferences=%s
                weather=%s
                pace=%s

                candidates:
                %s

                반드시 아래 JSON 형식만 출력한다.

                {
                  "recommendations": [
                    {
                      "providerId": "실제 후보 providerId",
                      "aiScore": 0부터 100 사이 숫자,
                      "reason": "짧은 추천 이유"
                    }
                  ]
                }
                """
                        .formatted(
                                limit,
                                request.latitude(),
                                request.longitude(),
                                request.resolvedPreferences(),
                                request.resolvedWeatherCondition(),
                                request.resolvedPace(),
                                candidatesString
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
            List<ScoredAttraction> candidates,
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
                    "Bedrock 관광지 추천 응답 형식이 올바르지 않습니다."
            );
        }


        Set<String> validProviderIds =
                new HashSet<>();


        for (ScoredAttraction candidate : candidates) {

            validProviderIds.add(
                    candidate
                            .attraction()
                            .providerId()
            );
        }


        List<AiDecision> decisions =
                new ArrayList<>();


        Set<String> used =
                new HashSet<>();


        for (JsonNode item : recommendations) {

            if (decisions.size() >= limit) {
                break;
            }


            String providerId =
                    item.path(
                            "providerId"
                    ).asText();


            if (!validProviderIds.contains(providerId)
                    || !used.add(providerId)) {

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
                            providerId,
                            aiScore,
                            reason
                    )
            );
        }


        return decisions;
    }


    private List<AttractionRecommendation>
    buildFinalRecommendations(

            List<ScoredAttraction> candidates,
            List<AiDecision> decisions,
            int limit
    ) {

        Map<String, ScoredAttraction> byId =
                new HashMap<>();


        for (ScoredAttraction candidate : candidates) {

            byId.put(
                    candidate
                            .attraction()
                            .providerId(),
                    candidate
            );
        }


        List<FinalScoredAttraction> result =
                new ArrayList<>();


        Set<String> selected =
                new HashSet<>();


        for (AiDecision decision : decisions) {

            ScoredAttraction candidate =
                    byId.get(
                            decision.providerId()
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
                    new FinalScoredAttraction(
                            candidate,
                            decision.aiScore(),
                            decision.reason(),
                            finalScore
                    )
            );


            selected.add(
                    decision.providerId()
            );
        }


        /*
         * Bedrock이 limit보다 적게 반환하면
         * 정량점수 순으로 보충.
         */
        candidates.stream()

                .filter(
                        candidate ->
                                !selected.contains(
                                        candidate
                                                .attraction()
                                                .providerId()
                                )
                )

                .sorted(
                        Comparator
                                .comparingDouble(
                                        ScoredAttraction::baseScore
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
                        candidate ->
                                result.add(
                                        new FinalScoredAttraction(
                                                candidate,
                                                null,
                                                null,
                                                candidate.baseScore()
                                        )
                                )
                );


        return result.stream()

                .sorted(
                        Comparator
                                .comparingDouble(
                                        FinalScoredAttraction::finalScore
                                )
                                .reversed()
                )

                .limit(limit)

                .map(
                        this::toRecommendation
                )

                .toList();
    }


    private AttractionRecommendation toRecommendation(
            FinalScoredAttraction finalItem
    ) {

        ScoredAttraction scored =
                finalItem.scored();


        TouristAttractionData attraction =
                scored.attraction();


        return new AttractionRecommendation(

                attraction.id(),

                attraction.provider(),
                attraction.providerId(),

                attraction.name(),

                attraction.categoryCode(),
                attraction.categoryName(),

                attraction.region1Code(),
                attraction.region1Name(),

                attraction.region2Code(),
                attraction.region2Name(),

                attraction.address(),
                attraction.roadAddress(),
                attraction.postcode(),

                attraction.latitude(),
                attraction.longitude(),

                attraction.tags(),
                attraction.allTags(),

                attraction.introduction(),
                attraction.phoneNumber(),

                attraction.photoId(),
                attraction.representativeImageUrl(),
                attraction.thumbnailImageUrl(),

                scored.distanceKm(),

                scored.estimatedDriveMinutes(),

                round(
                        scored.preferenceScore()
                                * 100.0,
                        2
                ),

                round(
                        scored.weatherScore()
                                * 100.0,
                        2
                ),

                round(
                        scored.paceScore()
                                * 100.0,
                        2
                ),

                round(
                        scored.baseScore()
                                * 100.0,
                        2
                ),

                finalItem.aiScore(),

                round(
                        finalItem.finalScore()
                                * 100.0,
                        2
                ),

                finalItem.reason()
        );
    }


    private String attractionText(
            TouristAttractionData attraction
    ) {

        return (
                nullToEmpty(
                        attraction.name()
                )
                        + " "
                        + nullToEmpty(
                        attraction.tags()
                )
                        + " "
                        + nullToEmpty(
                        attraction.allTags()
                )
                        + " "
                        + nullToEmpty(
                        attraction.introduction()
                )
        ).toLowerCase(
                Locale.ROOT
        );
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


    /**
     * ```json ... ```
     * 형태로 반환해도 파싱 가능하게 처리.
     */
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


    private record ScoredAttraction(

            TouristAttractionData attraction,

            double distanceKm,

            int estimatedDriveMinutes,

            double preferenceScore,

            double weatherScore,

            double paceScore,

            double baseScore

    ) {
    }


    private record CandidateWindow(

            int windowMinutes,

            List<ScoredAttraction> candidates

    ) {
    }


    private record AiDecision(

            String providerId,

            double aiScore,

            String reason

    ) {
    }


    private record FinalScoredAttraction(

            ScoredAttraction scored,

            Double aiScore,

            String reason,

            double finalScore

    ) {
    }
}