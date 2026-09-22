package com.travel.trip.plan.service;

import com.travel.attraction.data.TouristAttractionData;
import com.travel.attraction.repository.TouristAttractionRepository;
import com.travel.trip.entity.Trip;
import com.travel.trip.entity.TripPace;
import com.travel.trip.plan.dto.TripPlanCandidatePool;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TripPlanCandidatePromptCoverageTest {

    @Test
    void addsPromptNamedAttractionWhenItIsOutsideNormalCandidatePool() {
        TouristAttractionRepository repository = mock(TouristAttractionRepository.class);
        when(repository.findAllRecommendable()).thenReturn(List.of(
                source(10L, "한담해안산책로", 33.4626, 126.3108),
                source(20L, "새별오름", 33.366, 126.357)
        ));

        TripPlanCandidateService service = new TripPlanCandidateService(
                null, null, null, repository
        );

        @SuppressWarnings("unchecked")
        List<TripPlanCandidatePool.AttractionCandidate> result = ReflectionTestUtils.invokeMethod(
                service,
                "ensurePromptAttractions",
                trip("한담해안산책로는 3일차에 가고 싶어요"),
                List.of(new TripPlanCandidatePool.AttractionCandidate(
                        20L, "새별오름", "관광지", 33.366, 126.357,
                        1.0, 5, 0.9, "", "", false,
                        null, null, null, null
                ))
        );

        assertThat(result).isNotNull();
        assertThat(result).extracting(TripPlanCandidatePool.AttractionCandidate::id)
                .containsExactly(10L, 20L);
        assertThat(result.getFirst().recommendationReason())
                .contains("프롬프트");
    }

    @Test
    void doesNotDuplicateExistingPromptAttractionAndIgnoresMissingCoordinates() {
        TouristAttractionRepository repository = mock(TouristAttractionRepository.class);
        when(repository.findAllRecommendable()).thenReturn(List.of(
                source(10L, "한담해안산책로", 33.4626, 126.3108),
                source(30L, "좌표없는관광지", null, null)
        ));

        TripPlanCandidateService service = new TripPlanCandidateService(null, null, null, repository);
        List<TripPlanCandidatePool.AttractionCandidate> existing = List.of(
                new TripPlanCandidatePool.AttractionCandidate(
                        10L, "한담해안산책로", "관광지", 33.4626, 126.3108,
                        1.0, 5, 0.9, "", "", false,
                        null, null, null, null
                )
        );

        @SuppressWarnings("unchecked")
        List<TripPlanCandidatePool.AttractionCandidate> result = ReflectionTestUtils.invokeMethod(
                service,
                "ensurePromptAttractions",
                trip("한담해안산책로는 2일차, 좌표없는관광지는 3일차"),
                existing
        );

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(10L);
    }

    @Test
    void returnsOriginalCandidatesWhenPromptIsBlankOrDoesNotMatch() {
        TouristAttractionRepository repository = mock(TouristAttractionRepository.class);
        when(repository.findAllRecommendable()).thenReturn(List.of(source(10L, "한담해안산책로", 33.4, 126.3)));
        TripPlanCandidateService service = new TripPlanCandidateService(null, null, null, repository);
        List<TripPlanCandidatePool.AttractionCandidate> existing = List.of();

        @SuppressWarnings("unchecked")
        List<TripPlanCandidatePool.AttractionCandidate> blank = ReflectionTestUtils.invokeMethod(
                service, "ensurePromptAttractions", trip(" "), existing
        );
        @SuppressWarnings("unchecked")
        List<TripPlanCandidatePool.AttractionCandidate> unmatched = ReflectionTestUtils.invokeMethod(
                service, "ensurePromptAttractions", trip("성산일출봉은 2일차"), existing
        );

        assertThat(blank).isEmpty();
        assertThat(unmatched).isEmpty();
    }

    private Trip trip(String prompt) {
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
                .budget(1_000_000L)
                .mealBudgetPerPersonPerDay(40_000L)
                .pace(TripPace.BALANCED)
                .preferences(Set.of())
                .foodPreferences(Set.of())
                .prompt(prompt)
                .build();
    }

    private TouristAttractionData source(Long id, String name, Double lat, Double lon) {
        return new TouristAttractionData(
                id, "VISIT_JEJU", String.valueOf(id), name,
                "c1", "관광지", null, "제주특별자치도", null, "제주시",
                null, null, null, lat, lon,
                "자연", "자연", null, null,
                null, null, null, null, null
        );
    }
}
