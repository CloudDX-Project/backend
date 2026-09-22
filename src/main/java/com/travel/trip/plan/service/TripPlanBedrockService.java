package com.travel.trip.plan.service;

import com.travel.external.bedrock.BedrockClient;
import com.travel.flight.dto.FlightCandidate;
import com.travel.trip.entity.FoodPreference;
import com.travel.trip.entity.Trip;
import com.travel.trip.entity.TripPace;
import com.travel.trip.plan.dto.TripPlanCandidatePool;
import com.travel.trip.plan.type.TripPlanItemType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TripPlanBedrockService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    TripPlanBedrockService.class
            );

    private static final int FINAL_PLANNER_MAX_TOKENS =
            3500;

    private static final float FINAL_PLANNER_TEMPERATURE =
            0.15F;

    private final BedrockClient bedrockClient;
    private final JsonMapper jsonMapper;

    public TripPlanBedrockService(
            BedrockClient bedrockClient,
            JsonMapper jsonMapper
    ) {
        this.bedrockClient =
                bedrockClient;
        this.jsonMapper =
                jsonMapper;
    }

    public PlannerResult createPlan(
            Trip trip,
            TripPlanCandidatePool candidatePool,
            FlightCandidate outboundFlight,
            FlightCandidate returnFlight
    ) {

        try {

            String prompt =
                    buildPrompt(
                            trip,
                            candidatePool,
                            outboundFlight,
                            returnFlight
                    );

            String response =
                    bedrockClient.converse(
                            prompt,
                            FINAL_PLANNER_MAX_TOKENS,
                            FINAL_PLANNER_TEMPERATURE
                    );

            List<PlannedDay> days =
                    parseResponse(
                            response,
                            trip,
                            candidatePool
                    );

            return new PlannerResult(
                    "BEDROCK_FINAL_PLANNER",
                    days
            );

        } catch (Exception e) {

            log.warn(
                    "최종 여행일정 Bedrock 생성 실패. "
                            + "백엔드 fallback 일정으로 응답합니다.",
                    e
            );

            return new PlannerResult(
                    "BACKEND_FALLBACK_AFTER_BEDROCK_ERROR",
                    buildFallbackPlan(
                            trip,
                            candidatePool,
                            outboundFlight,
                            returnFlight
                    )
            );
        }
    }

    private String buildPrompt(
            Trip trip,
            TripPlanCandidatePool candidatePool,
            FlightCandidate outboundFlight,
            FlightCandidate returnFlight
    ) throws JacksonException {

        long totalDays =
                ChronoUnit.DAYS.between(
                        trip.getStartDate(),
                        trip.getEndDate()
                ) + 1;

        Map<String, Object> input =
                new LinkedHashMap<>();

        Map<String, Object> tripJson =
                new LinkedHashMap<>();

        tripJson.put(
                "tripId",
                trip.getId()
        );
        tripJson.put(
                "departure",
                trip.getDeparture()
        );
        tripJson.put(
                "destination",
                trip.getDestination()
        );
        tripJson.put(
                "startDate",
                trip.getStartDate()
        );
        tripJson.put(
                "startTime",
                trip.getStartTime()
        );
        tripJson.put(
                "endDate",
                trip.getEndDate()
        );
        tripJson.put(
                "endTime",
                trip.getEndTime()
        );
        tripJson.put(
                "totalDays",
                totalDays
        );
        tripJson.put(
                "peopleCount",
                trip.getPeopleCount()
        );
        tripJson.put(
                "mainTransportMode",
                trip.getMainTransportMode()
        );
        tripJson.put(
                "localTransportMode",
                trip.getLocalTransportMode()
        );
        tripJson.put(
                "pace",
                trip.getPace()
        );
        tripJson.put(
                "preferences",
                trip.getPreferences()
        );
        tripJson.put(
                "foodPreferences",
                trip.getFoodPreferences()
        );
        tripJson.put(
                "mealBudgetPerPersonPerDay",
                trip.getMealBudgetPerPersonPerDay()
        );

        input.put(
                "trip",
                tripJson
        );

        input.put(
                "selectedAccommodation",
                candidatePool.accommodation()
        );

        input.put(
                "mandatoryDestination",
                candidatePool.mandatoryDestination()
        );

        input.put(
                "selectedOutboundFlight",
                outboundFlight
        );

        input.put(
                "selectedReturnFlight",
                returnFlight
        );

        input.put(
                "dailyWeather",
                candidatePool.weather()
        );

        input.put(
                "attractionCandidates",
                candidatePool.attractions()
        );

        input.put(
                "restaurantCandidates",
                candidatePool.restaurants()
        );

        input.put(
                "cafeCandidates",
                candidatePool.cafes()
        );

        /*
         * 자유 입력 원문을 Bedrock에 넘기지 않는다.
         * 현재 지원하는 "관광지 + N일차"만 구조화해서 전달한다.
         */
        input.put(
                "promptDayConstraints",
                resolvePromptDayConstraints(
                        trip,
                        candidatePool
                )
        );

        String inputJson =
                jsonMapper.writeValueAsString(
                        input
                );

        return """
        당신은 대한민국 국내여행의 최종 일정 편성기다.

        입력에는 사용자가 확정한 숙소/왕복 항공편, 사용자가 직접 선택한 필수 목적지,
        관광지/식당/카페 후보와 Kakao Mobility 실제 도로 거리/시간 요약이 포함되어 있다.
        새로운 장소를 만들지 말고 입력 후보만 사용한다.

        ==================================================
        [절대 규칙]
        ==================================================
        1. days.items에는 ATTRACTION / RESTAURANT / CAFE만 반환한다.
        2. 후보에 없는 id를 생성하지 않는다.
        3. 동일 id는 여행 전체에서 중복 사용하지 않는다.
        4. mandatoryDestination이 존재하면 mandatory=true인 ATTRACTION을 여행 전체에서 정확히 1회 반드시 포함한다.
           사용자가 동문시장 같은 목적지를 직접 선택했다면 일정에서 누락시키면 안 된다.
        5. promptDayConstraints가 비어 있지 않다면 각 attractionId를 지정된 dayNumber에 정확히 1회 포함한다.
           이 관광지를 다른 날짜에 먼저 넣었다가 나중에 옮기는 방식으로 생각하지 말고,
           처음부터 해당 날짜의 식사/카페/다른 관광지와 동선을 함께 설계한다.
           promptDayConstraints는 관광지의 "방문 일차"만 강제한다. 별도의 시간대 요구는 없다.
        6. 자유 입력 원문은 제공되지 않는다. promptDayConstraints 이외의 자연어 요구를 추측하지 않는다.
        7. 항공/숙소/렌터카/공항 카드는 Backend가 삽입하므로 출력하지 않는다.

        ==================================================
        [실제 이동시간과 추천 품질]
        ==================================================
        후보의 actualDriveMinutesFromAccommodation / actualDistanceKmFromAccommodation,
        actualDriveMinutesFromDestination / actualDistanceKmFromDestination 값은
        Kakao Mobility 실제 도로 길찾기 요약값이다. null이 아니면 직선거리보다 반드시 우선한다.

        장소를 단순히 숙소에서 가까운 순서로 고르지 않는다.
        거리보다 장소의 품질과 사용자 적합도를 더 중요하게 본다.

        식당/카페 우선순위:
        - rating, reviewCount, qualityScore를 매우 중요하게 본다.
        - qualityScore와 추천 점수가 높은 후보를 우선한다.
        - 거리는 지나치게 비효율적인 이동을 제거하는 제약으로 사용하며 품질보다 우선하지 않는다.
        - 리뷰가 수천 개인 평점 4.6~4.8 후보가 조금 더 멀다는 이유만으로
          리뷰가 적고 평가가 낮은 근거리 후보에 밀리지 않게 한다.

        관광지는 현재 원천 데이터에 별점/리뷰 수가 없을 수 있으므로
        recommendationScore, landmarkScore, qualityScore, preferences, weather, 실제 도로 이동시간을 함께 본다.
        제주를 처음 방문한 여행자도 납득할 대표 명소를 일정의 중심으로 삼는다.
        협의회·사무소·일반 사업체처럼 관광 경험이 불명확한 장소는 선택하지 않는다.
        유명 명소와 지역다운 식사, 자연스러운 휴식 장소를 균형 있게 배치한다.

        하루 동선은 왕복 지그재그 이동을 피하되, 지나치게 좁은 반경 안의 장소만 고르지 않는다.
        별도 이유 없이 90분이 넘는 빈 시간을 만들지 않는다.
        이동과 입장 준비를 포함해 장소 사이에는 보통 15~40분의 현실적인 여유를 둔다.

        ==================================================
        [식사 - 하루 3식]
        ==================================================
        식사는 관광지보다 우선한다.

        중간 날짜(첫날/마지막날이 아닌 날):
        - BREAKFAST 07:30~10:00 : RESTAURANT 1개
        - LUNCH     11:30~14:00 : RESTAURANT 1개
        - DINNER    17:30~20:30 : RESTAURANT 1개
        가능한 한 정확히 3식을 배치한다.

        첫날:
        - 항공 도착 후 실제로 가능한 식사부터 배치한다.
        - 오전 도착이면 아침/점심/저녁 중 가능한 슬롯을 최대한 채운다.
        - 늦은 도착이면 이미 지난 식사 슬롯을 억지로 만들지 않는다.

        마지막날:
        - 체크아웃 및 귀국편 시간 안에서 가능한 식사를 최대한 배치한다.
        - 항공 시간과 충돌하면 식사 수를 줄일 수 있다.

        [식사 시간대 적합성]
        각 식당의 breakfastFitScore / lunchFitScore / dinnerFitScore를 반드시 참고한다.
        - 아침: 전복죽, 죽, 국밥, 해장국, 순두부, 미역국, 국수, 김밥, 브런치 등 아침에 자연스러운 메뉴 우선
        - 점심: 일반적인 식사 후보를 폭넓게 허용
        - 저녁: 흑돼지, 삼겹살, 구이, 회, 횟집, 전골, 바비큐 등 저녁형 메뉴 우선
        예: 흑돼지/고기구이를 아침 식사로 배치하지 않는다.
        businessHours가 제공되면 해당 방문 시각에 영업 가능한 후보만 사용한다.

        중간 날짜의 마지막 현지 식사는 DINNER여야 하고, 그 뒤 숙소로 복귀하는 흐름을 전제로 한다.

        ==================================================
        [숙소 도착/체크아웃]
        ==================================================
        selectedAccommodation.checkInTime / checkOutTime을 일정의 고정 제약으로 본다.

        첫날:
        - checkInTime은 숙소에 들어갈 수 있는 가장 이른 시각이며 고정 방문 시각이 아니다.
        - 체크인을 위해 숙소를 중간 방문했다가 다시 외출하는 동선을 만들지 않는다.
        - 가능한 관광·카페·저녁 일정을 먼저 마친 뒤 숙소를 하루의 마지막 도착지로 삼는다.
        - 체크인 시간 때문에 점심/저녁을 비현실적으로 건너뛰지 않는다.

        중간 날:
        - 숙소에서 시작하고 저녁 식사 후 숙소로 돌아가는 흐름으로 구성한다.

        마지막날:
        - checkOutTime을 기준으로 숙소에서 나간 뒤 일정을 시작한다.
        - 단, 귀국편 때문에 더 일찍 나가야 하는 경우 항공편 안전 시간이 우선한다.

        ==================================================
        [CAFE 정책]
        ==================================================
        cafeCandidates가 비어 있지 않다면 CAFE 선호가 없어도 카페를 완전히 금지하지 않는다.

        trip.foodPreferences에 CAFE가 없는 경우:
        - 첫날 또는 중간 날짜에 적당히 배치한다.
        - 마지막 날은 원칙적으로 카페를 생략한다.
        - 2박3일이면 보통 전체 1~2회 정도가 자연스럽다.

        trip.foodPreferences에 CAFE가 있는 경우:
        - 가능한 날마다 카페를 포함한다.
        - 하루 전체를 쓰는 중간 날짜에는 카페 2회도 허용한다.
        - 마지막 날도 항공 시간에 여유가 있으면 1회 가능하다.

        카페는 식사를 대체하지 않는다.
        카페 때문에 아침/점심/저녁 RESTAURANT를 삭제하지 않는다.

        ==================================================
        [항공/시간]
        ==================================================
        첫날 현지 일정은 selectedOutboundFlight.arrivalTime 이후부터 시작한다.
        착륙 후 하차·수하물 수령 30분을 확보한다.
        렌터카는 공항→업체 셔틀 시간과 인수 20분을 추가한 뒤 첫 장소로 이동한다.

        마지막 날은 selectedReturnFlight.departureTime보다 충분히 먼저 현지 일정을 끝낸다.
        국내선은 공항 도착 목표를 최소 출발 90분 전으로 둔다.
        렌터카 이용 시 마지막 장소→반납 업체 이동 + 반납 20분 + 셔틀 시간을 모두 포함한다.
        항공편 출발·도착 시각은 변경하지 않는다. 마지막 날에 시간이 부족하면 관광·식사를 줄인다.
        위 여유시간은 일정용 기본값이며 항공사 규정을 의미하지 않는다.

        startTime은 Backend가 실제 Kakao 구간시간으로 최종 재정렬하기 전의 목표 방문 시각이다.
        이동시간을 무시해 앞 일정 종료보다 이른 시간을 만들지 않는다.

        ==================================================
        [관광/날씨/PACE]
        ==================================================
        식사, 체크인/체크아웃, 필수 목적지를 먼저 확보한 뒤 관광지를 배치한다.
        RAIN/SNOW는 실내 또는 날씨 영향을 덜 받는 후보를 우선한다.
        SUNNY/CLOUDY는 야외/자연 후보를 적극 활용한다.

        RELAXED: 관광지 수를 줄이고 체류시간을 늘린다. 식사는 생략하지 않는다.
        BALANCED: 식사/관광/카페를 균형 있게 구성한다.
        ACTIVE: 이동 가능한 범위에서 관광지를 더 배치하되 식사는 생략하지 않는다.

        권장 체류시간:
        - ATTRACTION: RELAXED 90~150분, BALANCED 60~120분, ACTIVE 45~90분
        - RESTAURANT: 60~90분
        - CAFE: 45~90분

        ==================================================
        [출력 JSON]
        ==================================================
        JSON 객체만 반환한다. Markdown code fence와 설명문은 금지한다.

        {
          "days": [
            {
              "dayNumber": 1,
              "items": [
                {
                  "type": "RESTAURANT",
                  "id": 456,
                  "startTime": "12:10",
                  "stayMinutes": 75,
                  "reason": "점심 시간대 적합성과 높은 평점/리뷰를 우선"
                },
                {
                  "type": "ATTRACTION",
                  "id": 123,
                  "startTime": "14:00",
                  "stayMinutes": 90,
                  "reason": "필수 목적지 및 실제 이동 동선 반영"
                }
              ]
            }
          ]
        }

        모든 여행 날짜의 dayNumber를 반드시 반환한다.

        반환 전 자체 검증:
        - mandatory=true 목적지가 정확히 1회 포함되었는가?
        - promptDayConstraints의 attractionId가 각각 지정된 dayNumber에 정확히 1회 포함되었는가?
        - 중간 날짜에 아침/점심/저녁 3식이 있는가?
        - 아침에 흑돼지/구이/횟집 같은 저녁형 식당을 넣지 않았는가?
        - 중간 날짜의 마지막 현지 식사가 저녁인가?
        - 첫날 체크인 시간과 마지막날 체크아웃 시간을 고려했는가?
        - 카페가 식사를 밀어내지 않았는가?
        - rating/reviewCount가 좋은 후보가 단순 거리 때문에 불합리하게 배제되지 않았는가?
        - 항공편 시간과 충돌하지 않는가?

        ==================================================
        [입력 JSON]
        ==================================================
        """
                + inputJson;
    }

    private List<PlannedDay> parseResponse(
            String response,
            Trip trip,
            TripPlanCandidatePool candidatePool
    ) throws JacksonException {

        String json =
                extractJson(
                        response
                );

        JsonNode root =
                jsonMapper.readTree(
                        json
                );

        JsonNode daysNode =
                root.path(
                        "days"
                );

        if (!daysNode.isArray()) {
            throw new IllegalStateException(
                    "Bedrock 일정 응답의 days가 배열이 아닙니다."
            );
        }

        int totalDays =
                (int) ChronoUnit.DAYS.between(
                        trip.getStartDate(),
                        trip.getEndDate()
                ) + 1;

        Set<Long> validAttractionIds =
                new HashSet<>();

        candidatePool.attractions()
                .forEach(
                        item ->
                                validAttractionIds.add(
                                        item.id()
                                )
                );

        Set<Long> validRestaurantIds =
                new HashSet<>();

        candidatePool.restaurants()
                .forEach(
                        item ->
                                validRestaurantIds.add(
                                        item.id()
                                )
                );

        Set<Long> validCafeIds =
                new HashSet<>();

        candidatePool.cafes()
                .forEach(
                        item ->
                                validCafeIds.add(
                                        item.id()
                                )
                );

        Set<String> usedPlaceKeys =
                new HashSet<>();

        Set<Integer> usedDays =
                new HashSet<>();

        List<PlannedDay> result =
                new ArrayList<>();

        for (JsonNode dayNode : daysNode) {

            int dayNumber =
                    dayNode.path(
                            "dayNumber"
                    ).asInt(-1);

            if (
                    dayNumber < 1
                            || dayNumber > totalDays
                            || !usedDays.add(dayNumber)
            ) {
                continue;
            }

            JsonNode itemsNode =
                    dayNode.path(
                            "items"
                    );

            if (!itemsNode.isArray()) {
                continue;
            }

            List<PlannedItem> items =
                    new ArrayList<>();

            for (JsonNode itemNode : itemsNode) {

                String rawType =
                        itemNode.path(
                                "type"
                        ).asText();

                TripPlanItemType type;

                try {
                    type =
                            TripPlanItemType.valueOf(
                                    rawType
                            );
                } catch (Exception e) {
                    continue;
                }

                if (
                        type != TripPlanItemType.ATTRACTION
                                && type != TripPlanItemType.RESTAURANT
                                && type != TripPlanItemType.CAFE
                ) {
                    continue;
                }

                Long id =
                        itemNode.path(
                                "id"
                        ).asLong(-1L);

                if (id <= 0L) {
                    continue;
                }

                if (!isValidCandidateId(
                        type,
                        id,
                        validAttractionIds,
                        validRestaurantIds,
                        validCafeIds
                )) {
                    continue;
                }

                String placeKey =
                        type.name()
                                + ":"
                                + id;

                if (!usedPlaceKeys.add(placeKey)) {
                    continue;
                }

                LocalTime startTime =
                        parseTime(
                                itemNode.path(
                                        "startTime"
                                ).asText()
                        );

                int stayMinutes =
                        itemNode.path(
                                "stayMinutes"
                        ).asInt(
                                defaultStayMinutes(
                                        type,
                                        trip.getPace()
                                )
                        );

                stayMinutes =
                        Math.max(
                                30,
                                Math.min(
                                        stayMinutes,
                                        240
                                )
                        );

                String reason =
                        itemNode.path(
                                "reason"
                        ).asText();

                items.add(
                        new PlannedItem(
                                type,
                                id,
                                startTime,
                                stayMinutes,
                                reason
                        )
                );
            }

            result.add(
                    new PlannedDay(
                            dayNumber,
                            items
                    )
            );
        }

        result.sort(
                Comparator.comparingInt(
                        PlannedDay::dayNumber
                )
        );

        if (result.size() != totalDays) {
            throw new IllegalStateException(
                    "Bedrock 일정 응답에 모든 여행 일자가 포함되지 않았습니다."
            );
        }

        candidatePool.attractions()
                .stream()
                .filter(TripPlanCandidatePool.AttractionCandidate::mandatory)
                .findFirst()
                .ifPresent(mandatory -> {
                    String mandatoryKey =
                            TripPlanItemType.ATTRACTION.name() + ":" + mandatory.id();

                    if (!usedPlaceKeys.contains(mandatoryKey)) {
                        throw new IllegalStateException(
                                "Bedrock 일정에서 사용자가 선택한 필수 목적지가 누락되었습니다."
                        );
                    }
                });

        validatePromptDayConstraints(
                trip,
                candidatePool,
                result
        );

        return result;
    }

    private List<TripPromptDayConstraintParser.DayConstraint> resolvePromptDayConstraints(
            Trip trip,
            TripPlanCandidatePool candidatePool
    ) {
        int totalDays =
                (int) ChronoUnit.DAYS.between(
                        trip.getStartDate(),
                        trip.getEndDate()
                ) + 1;

        List<TripPromptDayConstraintParser.NamedAttraction> candidates =
                candidatePool.attractions()
                        .stream()
                        .map(item -> new TripPromptDayConstraintParser.NamedAttraction(
                                item.id(),
                                item.name()
                        ))
                        .toList();

        return TripPromptDayConstraintParser.parse(
                trip.getPrompt(),
                totalDays,
                candidates
        );
    }

    private void validatePromptDayConstraints(
            Trip trip,
            TripPlanCandidatePool candidatePool,
            List<PlannedDay> days
    ) {
        for (TripPromptDayConstraintParser.DayConstraint constraint
                : resolvePromptDayConstraints(trip, candidatePool)) {

            boolean presentOnRequestedDay =
                    days.stream()
                            .filter(day -> day.dayNumber() == constraint.dayNumber())
                            .flatMap(day -> day.items().stream())
                            .anyMatch(item ->
                                    item.type() == TripPlanItemType.ATTRACTION
                                            && item.id().equals(constraint.attractionId())
                            );

            if (!presentOnRequestedDay) {
                throw new IllegalStateException(
                        "Bedrock 일정이 프롬프트 방문 일차를 지키지 않았습니다: "
                                + constraint.attractionName()
                                + " -> "
                                + constraint.dayNumber()
                                + "일차"
                );
            }
        }
    }

    private boolean isValidCandidateId(
            TripPlanItemType type,
            Long id,
            Set<Long> validAttractionIds,
            Set<Long> validRestaurantIds,
            Set<Long> validCafeIds
    ) {

        return switch (type) {
            case ATTRACTION ->
                    validAttractionIds.contains(id);

            case RESTAURANT ->
                    validRestaurantIds.contains(id);

            case CAFE ->
                    validCafeIds.contains(id);

            default ->
                    false;
        };
    }

    private LocalTime parseTime(
            String value
    ) {

        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalTime.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private int defaultStayMinutes(
            TripPlanItemType type,
            TripPace pace
    ) {

        return switch (type) {
            case ATTRACTION ->
                    switch (pace) {
                        case RELAXED -> 120;
                        case BALANCED -> 90;
                        case ACTIVE -> 60;
                    };

            case RESTAURANT -> 75;

            case CAFE -> 60;

            default -> 0;
        };
    }

    private List<PlannedDay> buildFallbackPlan(
            Trip trip,
            TripPlanCandidatePool candidatePool,
            FlightCandidate outboundFlight,
            FlightCandidate returnFlight
    ) {
        int totalDays =
                (int) ChronoUnit.DAYS.between(
                        trip.getStartDate(),
                        trip.getEndDate()
                ) + 1;

        Set<Long> usedRestaurants = new HashSet<>();
        Set<Long> usedAttractions = new HashSet<>();
        Set<Long> usedCafes = new HashSet<>();

        List<TripPromptDayConstraintParser.DayConstraint> promptDayConstraints =
                resolvePromptDayConstraints(
                        trip,
                        candidatePool
                );

        TripPlanCandidatePool.AttractionCandidate mandatory =
                candidatePool.attractions()
                        .stream()
                        .filter(TripPlanCandidatePool.AttractionCandidate::mandatory)
                        .findFirst()
                        .orElse(null);

        int mandatoryDay =
                mandatory == null
                        ? (totalDays >= 3 ? 2 : 1)
                        : promptDayConstraints.stream()
                        .filter(item -> item.attractionId().equals(mandatory.id()))
                        .map(TripPromptDayConstraintParser.DayConstraint::dayNumber)
                        .findFirst()
                        .orElse(totalDays >= 3 ? 2 : 1);

        boolean cafePreferred =
                trip.getFoodPreferences().contains(FoodPreference.CAFE);

        List<PlannedDay> days = new ArrayList<>();

        for (int dayNumber = 1; dayNumber <= totalDays; dayNumber++) {
            boolean firstDay = dayNumber == 1;
            boolean lastDay = dayNumber == totalDays;

            List<PlannedItem> items = new ArrayList<>();
            LocalTime cursor = defaultDayStartTime(
                    dayNumber,
                    trip,
                    candidatePool,
                    outboundFlight
            );

            // 중간 날은 기본적으로 3식, 첫/마지막 날은 실제 이용 가능 시간에 맞춰 가능한 식사를 넣는다.
            if (!firstDay && cursor.isBefore(LocalTime.of(9, 30))) {
                TripPlanCandidatePool.RestaurantCandidate breakfast =
                        pickRestaurant(candidatePool.restaurants(), usedRestaurants, MealSlot.BREAKFAST);
                if (breakfast != null) {
                    LocalTime time = maxTime(cursor, LocalTime.of(8, 0));
                    items.add(new PlannedItem(
                            TripPlanItemType.RESTAURANT, breakfast.id(), time, 60,
                            "아침 식사 적합도와 평점/리뷰 품질을 우선한 fallback 식사"
                    ));
                    usedRestaurants.add(breakfast.id());
                    cursor = time.plusMinutes(90);
                }
            }

            if (mandatory != null
                    && dayNumber == mandatoryDay
                    && usedAttractions.add(mandatory.id())) {
                if (cursor.isBefore(LocalTime.of(10, 0))) {
                    cursor = LocalTime.of(10, 0);
                }
                items.add(new PlannedItem(
                        TripPlanItemType.ATTRACTION, mandatory.id(), cursor,
                        defaultStayMinutes(TripPlanItemType.ATTRACTION, trip.getPace()),
                        "사용자가 직접 선택한 필수 목적지"
                ));
                cursor = cursor.plusMinutes(
                        defaultStayMinutes(TripPlanItemType.ATTRACTION, trip.getPace()) + 30L
                );
            }

            if (cursor.isBefore(LocalTime.of(14, 0))) {
                TripPlanCandidatePool.RestaurantCandidate lunch =
                        pickRestaurant(candidatePool.restaurants(), usedRestaurants, MealSlot.LUNCH);
                if (lunch != null) {
                    LocalTime time = maxTime(cursor, LocalTime.of(11, 30));
                    if (time.isBefore(LocalTime.of(14, 15))) {
                        items.add(new PlannedItem(
                                TripPlanItemType.RESTAURANT, lunch.id(), time, 75,
                                "점심 시간대 적합도와 평점/리뷰 품질을 우선한 fallback 식사"
                        ));
                        usedRestaurants.add(lunch.id());
                        cursor = time.plusMinutes(105);
                    }
                }
            }

            int promptAttractionsToday = 0;
            for (TripPromptDayConstraintParser.DayConstraint constraint : promptDayConstraints) {
                if (constraint.dayNumber() != dayNumber
                        || usedAttractions.contains(constraint.attractionId())) {
                    continue;
                }

                TripPlanCandidatePool.AttractionCandidate required =
                        findAttractionById(
                                candidatePool.attractions(),
                                constraint.attractionId()
                        );

                if (required == null) {
                    continue;
                }

                int stay = defaultStayMinutes(
                        TripPlanItemType.ATTRACTION,
                        trip.getPace()
                );

                items.add(new PlannedItem(
                        TripPlanItemType.ATTRACTION,
                        required.id(),
                        cursor,
                        stay,
                        "사용자가 " + dayNumber + "일차 방문을 지정한 관광지"
                ));
                usedAttractions.add(required.id());
                promptAttractionsToday++;
                cursor = cursor.plusMinutes(stay + 30L);
            }

            int attractionTarget = switch (trip.getPace()) {
                case RELAXED -> 1;
                case BALANCED -> 2;
                case ACTIVE -> 3;
            };

            int genericAttractionTarget =
                    Math.max(0, attractionTarget - promptAttractionsToday);

            for (int i = 0; i < genericAttractionTarget; i++) {
                TripPlanCandidatePool.AttractionCandidate attraction =
                        pickAttraction(candidatePool.attractions(), usedAttractions);
                if (attraction == null || cursor.isAfter(LocalTime.of(17, 0))) {
                    break;
                }

                int stay = defaultStayMinutes(TripPlanItemType.ATTRACTION, trip.getPace());
                items.add(new PlannedItem(
                        TripPlanItemType.ATTRACTION, attraction.id(), cursor, stay,
                        "추천 점수와 실제 도로 이동 효율을 함께 고려한 fallback 관광지"
                ));
                usedAttractions.add(attraction.id());
                cursor = cursor.plusMinutes(stay + 30L);
            }

            int cafeTarget;
            if (cafePreferred) {
                cafeTarget = (!firstDay && !lastDay) ? 2 : 1;
            } else {
                cafeTarget = lastDay ? 0 : 1;
            }

            for (int i = 0; i < cafeTarget; i++) {
                TripPlanCandidatePool.CafeCandidate cafe =
                        pickCafe(candidatePool.cafes(), usedCafes);
                if (cafe == null || cursor.isAfter(LocalTime.of(17, 30))) {
                    break;
                }

                items.add(new PlannedItem(
                        TripPlanItemType.CAFE, cafe.id(), cursor, 60,
                        cafePreferred
                                ? "카페 선호와 평점/리뷰 품질을 반영"
                                : "식사를 방해하지 않는 범위에서 휴식용 카페를 배치"
                ));
                usedCafes.add(cafe.id());
                cursor = cursor.plusMinutes(90);
            }

            boolean dinnerAllowed =
                    !lastDay
                            || returnFlight == null
                            || returnFlight.departureTime() == null
                            || returnFlight.departureTime().toLocalTime().isAfter(LocalTime.of(20, 30));

            if (dinnerAllowed) {
                TripPlanCandidatePool.RestaurantCandidate dinner =
                        pickRestaurant(candidatePool.restaurants(), usedRestaurants, MealSlot.DINNER);
                if (dinner != null) {
                    LocalTime time = maxTime(cursor, LocalTime.of(17, 30));
                    if (time.isBefore(LocalTime.of(20, 31))) {
                        items.add(new PlannedItem(
                                TripPlanItemType.RESTAURANT, dinner.id(), time, 75,
                                "저녁 식사 적합도와 평점/리뷰 품질을 우선한 fallback 식사"
                        ));
                        usedRestaurants.add(dinner.id());
                    }
                }
            }

            items.sort(Comparator.comparing(
                    item -> item.startTime() == null ? LocalTime.MAX : item.startTime()
            ));

            days.add(new PlannedDay(dayNumber, items));
        }

        // 여행이 짧거나 항공 시간이 빡빡해 지정된 날에 못 넣은 경우 필수 목적지는 첫날에 강제 보강한다.
        if (mandatory != null && !usedAttractions.contains(mandatory.id()) && !days.isEmpty()) {
            PlannedDay first = days.get(0);
            List<PlannedItem> corrected = new ArrayList<>(first.items());
            corrected.add(new PlannedItem(
                    TripPlanItemType.ATTRACTION, mandatory.id(), LocalTime.of(14, 30),
                    defaultStayMinutes(TripPlanItemType.ATTRACTION, trip.getPace()),
                    "사용자가 직접 선택한 필수 목적지"
            ));
            corrected.sort(Comparator.comparing(
                    item -> item.startTime() == null ? LocalTime.MAX : item.startTime()
            ));
            days.set(0, new PlannedDay(first.dayNumber(), corrected));
        }

        return days;
    }

    private TripPlanCandidatePool.AttractionCandidate findAttractionById(
            List<TripPlanCandidatePool.AttractionCandidate> candidates,
            Long id
    ) {
        return candidates.stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    private TripPlanCandidatePool.RestaurantCandidate pickRestaurant(
            List<TripPlanCandidatePool.RestaurantCandidate> candidates,
            Set<Long> usedIds,
            MealSlot slot
    ) {
        return candidates.stream()
                .filter(item -> !usedIds.contains(item.id()))
                .max(Comparator.comparingDouble(item -> restaurantSlotScore(item, slot)))
                .orElse(null);
    }

    private double restaurantSlotScore(
            TripPlanCandidatePool.RestaurantCandidate item,
            MealSlot slot
    ) {
        double fit = switch (slot) {
            case BREAKFAST -> safe(item.breakfastFitScore());
            case LUNCH -> safe(item.lunchFitScore());
            case DINNER -> safe(item.dinnerFitScore());
        };

        // 품질 65%, 시간대 적합도 30%, 거리 5% 수준으로 거리 영향력을 제한한다.
        double quality = safe(item.qualityScore());
        double route = routeConvenience(item.actualDriveMinutesFromAccommodation());
        return quality * 0.65 + fit * 0.30 + route * 0.05;
    }

    private TripPlanCandidatePool.AttractionCandidate pickAttraction(
            List<TripPlanCandidatePool.AttractionCandidate> candidates,
            Set<Long> usedIds
    ) {
        return candidates.stream()
                .filter(item -> !item.mandatory())
                .filter(item -> !usedIds.contains(item.id()))
                .max(Comparator.comparingDouble(item ->
                        safe(item.recommendationScore()) * 0.85
                                + routeConvenience(item.actualDriveMinutesFromAccommodation()) * 0.15
                ))
                .orElse(null);
    }

    private TripPlanCandidatePool.CafeCandidate pickCafe(
            List<TripPlanCandidatePool.CafeCandidate> candidates,
            Set<Long> usedIds
    ) {
        return candidates.stream()
                .filter(item -> !usedIds.contains(item.id()))
                .max(Comparator.comparingDouble(item ->
                        safe(item.qualityScore()) * 0.75
                                + safe(item.recommendationScore()) * 0.20
                                + routeConvenience(item.actualDriveMinutesFromAccommodation()) * 0.05
                ))
                .orElse(null);
    }

    private double routeConvenience(Integer minutes) {
        if (minutes == null) return 0.5;
        return Math.max(0.0, 1.0 - Math.min(60, minutes) / 60.0);
    }

    private double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    private LocalTime maxTime(LocalTime a, LocalTime b) {
        return a.isAfter(b) ? a : b;
    }

    private enum MealSlot {
        BREAKFAST, LUNCH, DINNER
    }

    private LocalTime defaultDayStartTime(
            int dayNumber,
            Trip trip,
            TripPlanCandidatePool candidatePool,
            FlightCandidate outboundFlight
    ) {
        if (dayNumber == 1
                && outboundFlight != null
                && outboundFlight.arrivalTime() != null) {
            return outboundFlight.arrivalTime().toLocalTime().plusMinutes(60);
        }

        if (dayNumber == 1) {
            return trip.getStartTime();
        }

        int totalDays =
                (int) ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;

        if (dayNumber == totalDays) {
            String checkOut = candidatePool.accommodation().checkOutTime();
            if (checkOut != null && !checkOut.isBlank()) {
                try {
                    return LocalTime.parse(checkOut);
                } catch (Exception ignored) {
                    // fallback below
                }
            }
            return LocalTime.of(10, 0);
        }

        return LocalTime.of(8, 0);
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
                trimmed.indexOf('{');

        int end =
                trimmed.lastIndexOf('}');

        if (start < 0 || end < start) {
            throw new IllegalStateException(
                    "Bedrock 응답에서 JSON을 찾을 수 없습니다."
            );
        }

        return trimmed.substring(
                start,
                end + 1
        );
    }

    public record PlannerResult(

            String planner,

            List<PlannedDay> days

    ) {
    }

    public record PlannedDay(

            int dayNumber,

            List<PlannedItem> items

    ) {
    }

    public record PlannedItem(

            TripPlanItemType type,

            Long id,

            LocalTime startTime,

            int stayMinutes,

            String reason

    ) {
    }
}
