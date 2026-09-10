package com.travel.trip.plan.service;

import com.travel.external.bedrock.BedrockClient;
import com.travel.flight.dto.FlightCandidate;
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

        String inputJson =
                jsonMapper.writeValueAsString(
                        input
                );

        return """
        당신은 대한민국 국내여행 일정을 생성하는 전문 Travel Planner다.

        아래 입력에는 사용자가 직접 선택하여 확정한 숙소와 왕복 항공편,
        그리고 기존 추천 API에서 자체 점수 계산 + 1차 Bedrock reranking까지 완료된
        관광지/식당/카페 후보군이 들어 있다.

        당신의 역할은 새로운 장소를 추천하는 것이 아니다.

        이미 검증된 후보들 중에서 실제 방문할 장소를 선택하여
        날짜별로 자연스럽고 현실적인 여행 일정을 구성하는 것이다.

        ==================================================
        [가장 중요한 기본 원칙]
        ==================================================

        1. ATTRACTION / RESTAURANT / CAFE는
           반드시 입력 후보군에 존재하는 id만 사용한다.

        2. 입력 후보에 없는 장소나 id를 절대 생성하지 않는다.

        3. selectedAccommodation은 사용자가 메인 화면에서 직접 선택한
           확정 숙소다.

           숙소를 변경하거나 다른 숙소를 추천하지 않는다.

        4. selectedOutboundFlight와 selectedReturnFlight도
           사용자가 메인 화면에서 직접 선택한 확정 항공편이다.

           항공편을 변경하거나 새로 만들지 않는다.

        5. 숙소와 공항, 항공편은 Backend가 최종 일정에 직접 삽입한다.

           따라서 days.items에는 아래 세 종류만 반환한다.

           - ATTRACTION
           - RESTAURANT
           - CAFE

        ==================================================
        [여행의 전체 흐름]
        ==================================================

        AIR 여행은 다음 전체 흐름을 기준으로 한다.

        첫날:

        출발지역
        → 출발공항
        → 선택한 가는 항공편
        → 목적지 공항
        → 목적지 현지 일정
        → 선택 숙소

        중간 날짜:

        선택 숙소
        → 현지 일정
        → 선택 숙소

        마지막 날:

        선택 숙소
        → 현지 일정
        → 목적지 공항
        → 선택한 오는 항공편
        → 출발지역 공항
        → 원래 출발지역

        첫날의 현지 일정은
        selectedOutboundFlight.arrivalTime 이후부터 시작한다.

        목적지 공항 도착 직후에는
        수하물 수령, 렌터카 인수, 이동 준비 등을 고려하여
        약 45~60분 정도의 여유를 둔다.

        마지막 날은 selectedReturnFlight.departureTime보다
        충분히 앞서 목적지 공항으로 이동할 수 있도록 구성한다.

        국내선의 경우 실제 Routing API가 아직 없으므로
        대략 항공편 출발 90분 전까지는 현지 일정을 종료하는 방향으로
        보수적으로 판단한다.

        ==================================================
        [식사 규칙 - 매우 중요]
        ==================================================

        식사는 선택사항이 아니라 실제 여행 일정의 필수 요소다.

        관광지를 많이 넣기 위해 식사를 생략해서는 안 된다.

        가능한 경우 반드시 다음 시간대를 우선 확보한다.

        점심:
        - 11:30 ~ 14:00
        - RESTAURANT 1개

        저녁:
        - 17:00 ~ 20:00
        - RESTAURANT 1개

        중간 날짜처럼 하루 전체를 목적지에서 보내는 날은
        반드시 점심 1회 + 저녁 1회를 포함한다.

        예:
        숙소
        → 관광지
        → 점심
        → 관광지
        → 카페
        → 저녁
        → 숙소

        첫날은 목적지 공항 도착시간에 따라 판단한다.

        - 현지 일정 시작 가능 시간이 14:00 이전:
          점심과 저녁을 모두 포함한다.

        - 현지 일정 시작 가능 시간이 14:00 이후이고 17:00 이전:
          최소 저녁을 포함한다.

        - 17:00 이후 도착하는 매우 늦은 일정:
          현실적으로 가능한 경우에만 저녁을 포함한다.

        마지막 날은 귀국 항공편 시간에 따라 판단한다.

        - 귀국편 출발시간이 14:00 이후:
          가능한 경우 점심을 포함한다.

        - 귀국편 출발시간이 20:00 이후이고
          공항 이동시간까지 충분한 경우에만 저녁을 포함한다.

        - 귀국 항공편 때문에 시간이 부족한 경우에는
          마지막 날 저녁을 생략할 수 있다.

        중요한 규칙:

        중간 날짜에는 저녁 식사 없이
        오후 4시나 5시에 바로 숙소로 복귀하는 일정을 만들지 않는다.

        첫날 역시 현지에 오전 또는 낮에 도착했다면
        저녁 식사 전에 숙소로 일정을 끝내지 않는다.

        RESTAURANT 후보가 충분히 존재하는데
        식사를 생략하면 안 된다.

        같은 식당 id는 여행 전체에서 한 번만 사용한다.

        ==================================================
        [CAFE 규칙 - 매우 중요]
        ==================================================

        trip.foodPreferences에 CAFE가 포함되어 있고
        cafeCandidates가 비어 있지 않다면
        사용자가 카페 방문을 원한다고 판단한다.

        이 경우 카페를 일정에서 완전히 생략해서는 안 된다.

        2박 3일 정도의 여행이라면
        전체 여행 중 최소 1~2회의 CAFE 방문을 포함한다.

        하루 전체를 목적지에서 보내는 중간 날짜에는
        가능한 경우 CAFE 1개를 포함한다.

        첫날 현지 도착시간이 충분히 빠르다면
        관광지와 저녁 사이에 카페를 배치할 수 있다.

        마지막 날은 귀국 항공편 시간이 촉박하다면
        카페를 생략할 수 있다.

        카페의 자연스러운 배치 예:

        관광지
        → 점심
        → 관광지
        → 카페
        → 관광지 또는 저녁

        또는

        관광지
        → 카페
        → 저녁

        카페는 식당을 대체하지 않는다.

        CAFE를 넣었다는 이유로
        점심이나 저녁 RESTAURANT를 삭제하지 않는다.

        cafeCandidates가 비어 있으면
        CAFE를 절대 출력하지 않는다.

        ==================================================
        [관광지 규칙]
        ==================================================

        식사와 필요한 휴식 시간을 먼저 확보하고
        남는 시간에 관광지를 배치한다.

        관광지만 연속해서 지나치게 많이 넣지 않는다.

        추천점수 순서대로 단순 나열하지 않는다.

        다음 요소를 함께 고려한다.

        - 사용자 preferences
        - 날짜별 weather
        - 후보의 위도/경도
        - 식사 시간
        - 카페 선호
        - pace
        - 숙소 위치
        - 첫날 항공 도착시간
        - 마지막 날 항공 출발시간

        ==================================================
        [이동 동선 규칙]
        ==================================================

        selectedAccommodation의 latitude / longitude를
        목적지 여행의 기준 위치로 사용한다.

        localTransportMode가 RENTAL_CAR이면
        목적지 내 이동은 렌터카라고 가정한다.

        아직 실제 Routing API는 연결되지 않았으므로
        정확한 도로 이동시간을 계산하려고 하지 않는다.

        대신 후보의 latitude / longitude를 비교하여
        같은 날 가능한 한 가까운 지역끼리 묶는다.

        예를 들어 한 장소를 방문한 뒤
        반대편 지역으로 갔다가 다시 원래 지역으로 돌아오는 식의
        비효율적인 왕복 동선을 피한다.

        하루 일정은 가능한 한 하나의 이동 방향 또는
        인접 지역 중심으로 구성한다.

        ==================================================
        [날씨 규칙]
        ==================================================

        dailyWeather는 날짜별로 반드시 반영한다.

        RAIN 또는 SNOW인 날은
        실내 장소 또는 날씨 영향을 덜 받는 후보를 우선한다.

        SUNNY 또는 CLOUDY인 날은
        자연/야외 관광지를 보다 적극적으로 사용할 수 있다.

        날씨가 좋지 않더라도
        단순히 모든 관광지를 제거하지 말고
        후보 특성과 사용자 성향을 함께 판단한다.

        ==================================================
        [PACE 규칙]
        ==================================================

        RELAXED:
        - 관광지 개수를 줄인다.
        - 개별 장소 체류시간을 길게 잡는다.
        - 식사와 카페, 휴식은 생략하지 않는다.
        - RELAXED는 '일찍 숙소로 복귀'라는 뜻이 아니다.

        BALANCED:
        - 일반적인 여행 밀도로 구성한다.
        - 식사, 관광, 카페를 균형 있게 배치한다.

        ACTIVE:
        - 이동 가능한 범위에서 관광지를 더 많이 배치한다.
        - 그렇더라도 점심과 저녁을 생략하지 않는다.

        ==================================================
        [대략적인 체류시간]
        ==================================================

        ATTRACTION:
        - RELAXED: 약 90~150분
        - BALANCED: 약 60~120분
        - ACTIVE: 약 45~90분

        RESTAURANT:
        - 약 60~90분

        CAFE:
        - 약 45~90분

        ==================================================
        [시간 생성 규칙]
        ==================================================

        startTime은 각 장소의 예상 방문 시작 시각이다.

        실제 이동시간은 추후 Routing API를 연결하면
        Backend에서 다시 계산할 예정이다.

        현재는 위도/경도를 이용해 이동 시간을 대략 고려하되
        지나치게 촘촘하거나 비현실적인 시간을 만들지 않는다.

        이전 장소의 종료시간보다
        다음 장소의 startTime이 빠르면 안 된다.

        이동시간을 전혀 고려하지 않고
        장소 종료 직후 즉시 다른 장소가 시작되는 일정도 피한다.

        식사 시간대는 특히 우선적으로 지킨다.

        ==================================================
        [중복 금지]
        ==================================================

        동일한 장소 id를 여행 전체에서 중복 사용하지 않는다.

        ATTRACTION id 중복 금지.
        RESTAURANT id 중복 금지.
        CAFE id 중복 금지.

        ==================================================
        [일정 생성 우선순위]
        ==================================================

        일정 생성 시 다음 우선순위를 따른다.

        1순위: 확정된 항공편 시간 준수
        2순위: 점심 / 저녁 식사 확보
        3순위: 사용자의 핵심 preferences와 foodPreferences 반영
        4순위: 날짜별 날씨
        5순위: 숙소 기준 지리적 동선
        6순위: 카페 및 휴식
        7순위: 관광지 추가 배치

        관광지 하나를 더 넣기 위해
        점심이나 저녁을 제거하지 않는다.

        ==================================================
        [출력 형식]
        ==================================================

        반드시 아래와 같은 JSON 객체만 반환한다.

        Markdown code fence(```)를 사용하지 않는다.
        JSON 앞뒤로 설명 문장을 출력하지 않는다.

        {
          "days": [
            {
              "dayNumber": 1,
              "items": [
                {
                  "type": "ATTRACTION",
                  "id": 123,
                  "startTime": "10:30",
                  "stayMinutes": 90,
                  "reason": "선택 이유"
                },
                {
                  "type": "RESTAURANT",
                  "id": 456,
                  "startTime": "12:30",
                  "stayMinutes": 75,
                  "reason": "점심 식사와 사용자 음식 선호를 반영"
                },
                {
                  "type": "CAFE",
                  "id": 789,
                  "startTime": "15:30",
                  "stayMinutes": 60,
                  "reason": "카페 선호와 동선을 반영"
                },
                {
                  "type": "RESTAURANT",
                  "id": 999,
                  "startTime": "18:30",
                  "stayMinutes": 75,
                  "reason": "저녁 식사와 이동 동선을 반영"
                }
              ]
            }
          ]
        }

        모든 여행 날짜에 해당하는 dayNumber를 반드시 반환한다.

        허용 type은 반드시 아래 세 가지뿐이다.

        ATTRACTION
        RESTAURANT
        CAFE

        숙소, 공항, 항공편, 출발지는 days.items에 넣지 않는다.
        해당 항목들은 Backend가 고정 일정으로 삽입한다.

        ==================================================
        [최종 자체 검증 후 응답]
        ==================================================

        JSON을 반환하기 전에 내부적으로 다음 사항을 반드시 확인한다.

        - 중간 날짜에 점심이 있는가?
        - 중간 날짜에 저녁이 있는가?
        - 첫날 오전/낮 도착인데 저녁이 누락되지 않았는가?
        - 마지막 날 귀국편 전 점심 시간이 충분한데 점심이 누락되지 않았는가?
        - foodPreferences에 CAFE가 있는데 여행 전체에 카페가 하나도 없는가?
        - 동일 장소 id가 중복되었는가?
        - 항공편 시간과 일정이 충돌하는가?
        - 마지막 날 공항 이동 시간이 확보되어 있는가?
        - RELAXED라는 이유로 식사를 생략하지 않았는가?

        하나라도 문제가 있다면
        조건을 만족하도록 일정 자체를 수정한 뒤 최종 JSON을 반환한다.

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

        return result;
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

        int attractionIndex = 0;
        int restaurantIndex = 0;
        int cafeIndex = 0;

        List<PlannedDay> days =
                new ArrayList<>();

        for (int dayNumber = 1;
             dayNumber <= totalDays;
             dayNumber++) {

            List<PlannedItem> items =
                    new ArrayList<>();

            LocalTime cursor =
                    defaultDayStartTime(
                            dayNumber,
                            trip,
                            outboundFlight
                    );

            int attractionTarget =
                    switch (trip.getPace()) {
                        case RELAXED -> 1;
                        case BALANCED -> 2;
                        case ACTIVE -> 3;
                    };

            if (
                    restaurantIndex
                            < candidatePool.restaurants().size()
            ) {

                TripPlanCandidatePool.RestaurantCandidate restaurant =
                        candidatePool.restaurants()
                                .get(
                                        restaurantIndex++
                                );

                LocalTime mealTime =
                        cursor.isBefore(
                                LocalTime.of(
                                        11,
                                        30
                                )
                        )
                                ? LocalTime.of(
                                12,
                                0
                        )
                                : cursor;

                items.add(
                        new PlannedItem(
                                TripPlanItemType.RESTAURANT,
                                restaurant.id(),
                                mealTime,
                                75,
                                "추천 점수 상위 식당을 일정에 배치했습니다."
                        )
                );

                cursor =
                        mealTime.plusMinutes(
                                105
                        );
            }

            for (int i = 0;
                 i < attractionTarget
                         && attractionIndex
                         < candidatePool.attractions().size();
                 i++) {

                TripPlanCandidatePool.AttractionCandidate attraction =
                        candidatePool.attractions()
                                .get(
                                        attractionIndex++
                                );

                int stay =
                        defaultStayMinutes(
                                TripPlanItemType.ATTRACTION,
                                trip.getPace()
                        );

                items.add(
                        new PlannedItem(
                                TripPlanItemType.ATTRACTION,
                                attraction.id(),
                                cursor,
                                stay,
                                "추천 점수 상위 관광지를 일정에 배치했습니다."
                        )
                );

                cursor =
                        cursor.plusMinutes(
                                stay + 45L
                        );
            }

            if (
                    cafeIndex
                            < candidatePool.cafes().size()
            ) {

                TripPlanCandidatePool.CafeCandidate cafe =
                        candidatePool.cafes()
                                .get(
                                        cafeIndex++
                                );

                items.add(
                        new PlannedItem(
                                TripPlanItemType.CAFE,
                                cafe.id(),
                                cursor,
                                60,
                                "사용자의 카페 선호를 반영했습니다."
                        )
                );
            }

            days.add(
                    new PlannedDay(
                            dayNumber,
                            items
                    )
            );
        }

        return days;
    }

    private LocalTime defaultDayStartTime(
            int dayNumber,
            Trip trip,
            FlightCandidate outboundFlight
    ) {

        if (
                dayNumber == 1
                        && outboundFlight != null
                        && outboundFlight.arrivalTime() != null
        ) {

            return outboundFlight
                    .arrivalTime()
                    .toLocalTime()
                    .plusMinutes(60);
        }

        if (dayNumber == 1) {
            return trip.getStartTime();
        }

        return LocalTime.of(
                9,
                30
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