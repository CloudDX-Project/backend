package com.travel.trip.plan.service;

import com.travel.external.bedrock.BedrockClient;
import com.travel.trip.entity.FoodPreference;
import com.travel.trip.entity.LocalTransportMode;
import com.travel.trip.entity.MainTransportMode;
import com.travel.trip.entity.Trip;
import com.travel.trip.entity.TripPace;
import com.travel.trip.entity.TripPreference;
import com.travel.trip.plan.dto.TripPlanCandidatePool;
import com.travel.trip.plan.dto.TripPlanSelectedAccommodation;
import com.travel.trip.plan.type.TripPlanItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripPlanBedrockServiceCoverageTest {

    private BedrockClient bedrockClient;
    private TripPlanBedrockService service;

    @BeforeEach
    void setUp() {
        bedrockClient = mock(BedrockClient.class);
        service = new TripPlanBedrockService(
                bedrockClient,
                JsonMapper.builder().findAndAddModules().build()
        );
    }

    @Test
    void acceptsValidBedrockPlanWithPromptDayAndCafePreference() {
        when(bedrockClient.converse(anyString(), anyInt(), anyFloat()))
                .thenReturn(validJson());

        TripPlanBedrockService.PlannerResult result = service.createPlan(
                trip(), pool(), null, null
        );

        assertThat(result.planner()).isEqualTo("BEDROCK_FINAL_PLANNER");
        assertThat(result.days()).hasSize(3);
        assertThat(result.days().get(2).items())
                .anySatisfy(item -> {
                    assertThat(item.type()).isEqualTo(TripPlanItemType.ATTRACTION);
                    assertThat(item.id()).isEqualTo(10L);
                });
        assertThat(result.days().stream()
                .flatMap(day -> day.items().stream())
                .filter(item -> item.type() == TripPlanItemType.CAFE)
                .count()).isEqualTo(1L);
    }

    @Test
    void retriesWhenPromptAttractionIsOnWrongDay() {
        when(bedrockClient.converse(anyString(), anyInt(), anyFloat()))
                .thenReturn(wrongPromptDayJson(), validJson());

        TripPlanBedrockService.PlannerResult result = service.createPlan(
                trip(), pool(), null, null
        );

        assertThat(result.planner()).isEqualTo("BEDROCK_FINAL_PLANNER_RETRY");
        verify(bedrockClient, times(2)).converse(anyString(), anyInt(), anyFloat());
    }

    @Test
    void retriesWhenCafePreferenceIsMissing() {
        when(bedrockClient.converse(anyString(), anyInt(), anyFloat()))
                .thenReturn(noCafeJson(), validJson());

        TripPlanBedrockService.PlannerResult result = service.createPlan(
                trip(), pool(), null, null
        );

        assertThat(result.planner()).isEqualTo("BEDROCK_FINAL_PLANNER_RETRY");
        verify(bedrockClient, times(2)).converse(anyString(), anyInt(), anyFloat());
    }

    @Test
    void usesFallbackOnlyAfterTwoBedrockFailuresAndKeepsCafeAndPromptDay() {
        when(bedrockClient.converse(anyString(), anyInt(), anyFloat()))
                .thenThrow(new IllegalStateException("bedrock unavailable"));

        TripPlanBedrockService.PlannerResult result = service.createPlan(
                trip(), pool(), null, null
        );

        assertThat(result.planner()).isEqualTo("BACKEND_FALLBACK_AFTER_BEDROCK_ERROR");
        verify(bedrockClient, times(2)).converse(anyString(), anyInt(), anyFloat());

        long cafeCount = result.days().stream()
                .flatMap(day -> day.items().stream())
                .filter(item -> item.type() == TripPlanItemType.CAFE)
                .count();
        assertThat(cafeCount).isGreaterThanOrEqualTo(1L);

        long requestedDayCount = result.days().stream()
                .filter(day -> day.dayNumber() == 3)
                .flatMap(day -> day.items().stream())
                .filter(item -> item.type() == TripPlanItemType.ATTRACTION && item.id().equals(10L))
                .count();
        assertThat(requestedDayCount).isEqualTo(1L);
    }

    @Test
    void ignoresMarkdownFenceAndInvalidItemsWhileParsing() {
        String fenced = """
                ```json
                {
                  "days": [
                    {"dayNumber":1,"items":[
                      {"type":"UNKNOWN","id":999,"startTime":"bad","stayMinutes":1},
                      {"type":"CAFE","id":30,"startTime":"10:30","stayMinutes":60,"reason":"휴식"}
                    ]},
                    {"dayNumber":2,"items":[
                      {"type":"RESTAURANT","id":22,"startTime":"12:00","stayMinutes":500,"reason":"점심"}
                    ]},
                    {"dayNumber":3,"items":[
                      {"type":"ATTRACTION","id":10,"startTime":"14:00","stayMinutes":10,"reason":"지정 관광지"}
                    ]}
                  ]
                }
                ```
                """;
        when(bedrockClient.converse(anyString(), anyInt(), anyFloat())).thenReturn(fenced);

        TripPlanBedrockService.PlannerResult result = service.createPlan(trip(), pool(), null, null);

        assertThat(result.planner()).isEqualTo("BEDROCK_FINAL_PLANNER");
        assertThat(result.days().get(1).items().getFirst().stayMinutes()).isEqualTo(240);
        assertThat(result.days().get(2).items().getFirst().stayMinutes()).isEqualTo(30);
    }

    private Trip trip() {
        return Trip.builder()
                .departure("서울")
                .departureLatitude(37.56)
                .departureLongitude(126.97)
                .destination("제주")
                .destinationLatitude(33.49)
                .destinationLongitude(126.53)
                .startDate(LocalDate.of(2026, 9, 22))
                .startTime(LocalTime.of(9, 0))
                .endDate(LocalDate.of(2026, 9, 24))
                .endTime(LocalTime.of(18, 0))
                .peopleCount(1)
                .mainTransportMode(MainTransportMode.AIR)
                .localTransportMode(LocalTransportMode.RENTAL_CAR)
                .budget(1_000_000L)
                .mealBudgetPerPersonPerDay(40_000L)
                .pace(TripPace.ACTIVE)
                .preferences(Set.of(TripPreference.SIGHTSEEING))
                .foodPreferences(Set.of(FoodPreference.KOREAN, FoodPreference.CAFE))
                .prompt("한담해안산책로는 3일차에 가고 싶어요")
                .build();
    }

    private TripPlanCandidatePool pool() {
        return new TripPlanCandidatePool(
                new TripPlanSelectedAccommodation(
                        1L, "hotel", "애월 숙소", "제주시 애월읍",
                        33.456, 126.309, "15:00", "11:00"
                ),
                null,
                List.of(
                        attraction(10L, "한담해안산책로", 0.95),
                        attraction(11L, "새별오름", 0.90),
                        attraction(12L, "오설록 티 뮤지엄", 0.85),
                        attraction(13L, "협재해수욕장", 0.80)
                ),
                List.of(
                        restaurant(21L, "아침식당", 1.0, 0.3, 0.1),
                        restaurant(22L, "점심식당", 0.2, 1.0, 0.4),
                        restaurant(23L, "저녁식당", 0.1, 0.4, 1.0),
                        restaurant(24L, "국수집", 0.8, 0.8, 0.3),
                        restaurant(25L, "해물집", 0.1, 0.8, 0.9),
                        restaurant(26L, "한식집", 0.5, 0.8, 0.8)
                ),
                List.of(
                        cafe(30L, "애월카페"),
                        cafe(31L, "바다카페")
                ),
                List.of()
        );
    }

    private TripPlanCandidatePool.AttractionCandidate attraction(Long id, String name, double score) {
        return new TripPlanCandidatePool.AttractionCandidate(
                id, name, "관광지", 33.45 + id / 10000.0, 126.30 + id / 10000.0,
                2.0, 10, score, "", "추천", false,
                2.0, 10, 2.0, 10
        );
    }

    private TripPlanCandidatePool.RestaurantCandidate restaurant(
            Long id, String name, double breakfast, double lunch, double dinner
    ) {
        return new TripPlanCandidatePool.RestaurantCandidate(
                id, name, "한식", 33.46, 126.31,
                2.0, 10, 0.9, List.of("KOREAN"), "", "", "추천",
                4.7, 500, "", 0.9,
                breakfast, lunch, dinner,
                2.0, 10, 2.0, 10
        );
    }

    private TripPlanCandidatePool.CafeCandidate cafe(Long id, String name) {
        return new TripPlanCandidatePool.CafeCandidate(
                id, name, "카페", 33.47, 126.32,
                2.0, 10, 0.9, "", "", "추천",
                4.8, 800, "", 0.95,
                2.0, 10, 2.0, 10
        );
    }

    private String validJson() {
        return """
                {"days":[
                  {"dayNumber":1,"items":[
                    {"type":"CAFE","id":30,"startTime":"14:00","stayMinutes":60,"reason":"카페 선호"},
                    {"type":"RESTAURANT","id":21,"startTime":"18:00","stayMinutes":75,"reason":"저녁"}
                  ]},
                  {"dayNumber":2,"items":[
                    {"type":"RESTAURANT","id":22,"startTime":"12:00","stayMinutes":75,"reason":"점심"},
                    {"type":"ATTRACTION","id":11,"startTime":"14:00","stayMinutes":60,"reason":"관광"}
                  ]},
                  {"dayNumber":3,"items":[
                    {"type":"ATTRACTION","id":10,"startTime":"12:00","stayMinutes":60,"reason":"지정 관광지"},
                    {"type":"RESTAURANT","id":23,"startTime":"14:00","stayMinutes":75,"reason":"식사"}
                  ]}
                ]}
                """;
    }

    private String wrongPromptDayJson() {
        return """
                {"days":[
                  {"dayNumber":1,"items":[{"type":"CAFE","id":30,"startTime":"14:00","stayMinutes":60,"reason":"카페"}]},
                  {"dayNumber":2,"items":[{"type":"ATTRACTION","id":10,"startTime":"14:00","stayMinutes":60,"reason":"잘못된 날짜"}]},
                  {"dayNumber":3,"items":[{"type":"RESTAURANT","id":23,"startTime":"14:00","stayMinutes":75,"reason":"식사"}]}
                ]}
                """;
    }

    private String noCafeJson() {
        return """
                {"days":[
                  {"dayNumber":1,"items":[{"type":"RESTAURANT","id":21,"startTime":"12:00","stayMinutes":75,"reason":"식사"}]},
                  {"dayNumber":2,"items":[{"type":"ATTRACTION","id":11,"startTime":"14:00","stayMinutes":60,"reason":"관광"}]},
                  {"dayNumber":3,"items":[{"type":"ATTRACTION","id":10,"startTime":"14:00","stayMinutes":60,"reason":"지정 관광지"}]}
                ]}
                """;
    }
}
