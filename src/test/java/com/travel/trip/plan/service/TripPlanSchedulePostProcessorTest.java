package com.travel.trip.plan.service;

import com.travel.routing.dto.DrivingRouteResult;
import com.travel.routing.service.RoutingService;
import com.travel.trip.entity.LocalTransportMode;
import com.travel.trip.entity.SegmentTransportMode;
import com.travel.trip.entity.Trip;
import com.travel.trip.entity.TripRentalSelection;
import com.travel.trip.plan.dto.TripPlanCandidatePool;
import com.travel.trip.plan.dto.TripPlanDayResponse;
import com.travel.trip.plan.dto.TripPlanItemResponse;
import com.travel.trip.plan.dto.TripPlanSelectedAccommodation;
import com.travel.trip.plan.type.TripPlanItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TripPlanSchedulePostProcessorTest {

    private RoutingService routingService;
    private TripPlanSchedulePostProcessor processor;

    @BeforeEach
    void setUp() {
        routingService = mock(RoutingService.class);
        processor = new TripPlanSchedulePostProcessor(routingService);
        when(routingService.findDrivingRoute(
                anyDouble(), anyDouble(), anyDouble(), anyDouble()
        )).thenReturn(new DrivingRouteResult(5_000, 20 * 60, 0, 0, List.of()));
    }

    @Test
    void repairsMiddleDayToThreeMealSlotsAndRejectsBlackPorkForBreakfast() {
        Trip trip = mock(Trip.class);
        when(trip.getLocalTransportMode()).thenReturn(LocalTransportMode.RENTAL_CAR);

        LocalDate date = LocalDate.of(2026, 9, 17);
        TripPlanCandidatePool pool = pool(
                List.of(
                        restaurant(1L, "미소흑돼지", 0.05, 0.72, 1.0),
                        restaurant(2L, "제주세호해장국", 1.0, 0.90, 0.55),
                        restaurant(3L, "두문포갈치", 0.45, 0.90, 0.85),
                        restaurant(4L, "종가전복", 0.45, 0.90, 0.85),
                        restaurant(5L, "세화그때그집", 0.05, 0.72, 1.0)
                ),
                List.of(),
                List.of()
        );

        TripPlanDayResponse middle = day(2, date, List.of(
                item(TripPlanItemType.ACCOMMODATION, null, "숙소", "DAY_START", date.atTime(7, 30), null, 33.5, 126.5),
                item(TripPlanItemType.RESTAURANT, 1L, "미소흑돼지", "한식", date.atTime(7, 30), date.atTime(8, 30), 33.51, 126.51),
                item(TripPlanItemType.RESTAURANT, 3L, "두문포갈치", "한식", date.atTime(10, 48), date.atTime(12, 3), 33.52, 126.52),
                item(TripPlanItemType.RESTAURANT, 4L, "종가전복", "한식", date.atTime(13, 30), date.atTime(14, 45), 33.53, 126.53),
                item(TripPlanItemType.RESTAURANT, 5L, "세화그때그집", "한식", date.atTime(16, 54), date.atTime(18, 9), 33.54, 126.54),
                item(TripPlanItemType.ACCOMMODATION, null, "숙소", "NIGHT_RETURN", null, null, 33.5, 126.5)
        ));

        List<TripPlanDayResponse> result = processor.repairMealSlots(
                trip,
                pool,
                List.of(day(1, date.minusDays(1), List.of()), middle, day(3, date.plusDays(1), List.of()))
        );

        List<TripPlanItemResponse> meals = result.get(1).items().stream()
                .filter(item -> item.type() == TripPlanItemType.RESTAURANT)
                .toList();

        assertThat(meals).hasSize(3);
        assertThat(meals.get(0).placeId()).isEqualTo(2L);
        assertThat(meals.stream()
                .map(item -> item.startAt().toLocalTime())
                .toList())
                .containsExactly(
                        java.time.LocalTime.of(8, 0),
                        java.time.LocalTime.of(12, 30),
                        java.time.LocalTime.of(18, 30)
                );
    }

    @Test
    void fillsLastDayWithCafeWhenNinetyMinuteAttractionDoesNotFit() {
        Trip trip = mock(Trip.class);
        TripRentalSelection rental = mock(TripRentalSelection.class);
        when(trip.getLocalTransportMode()).thenReturn(LocalTransportMode.RENTAL_CAR);
        when(trip.getSelectedRental()).thenReturn(rental);
        when(rental.getLatitude()).thenReturn(33.50);
        when(rental.getLongitude()).thenReturn(126.50);
        when(rental.getEstimatedShuttleMinutes()).thenReturn(20);

        LocalDate date = LocalDate.of(2026, 9, 19);
        TripPlanCandidatePool pool = pool(
                List.of(),
                List.of(attraction(10L, "관광지")),
                List.of(cafe(20L, "짧은 카페"))
        );

        TripPlanDayResponse lastDay = day(3, date, List.of(
                item(TripPlanItemType.ACCOMMODATION, null, "숙소", "CHECK_OUT", date.atTime(11, 0), null, 33.40, 126.40),
                item(TripPlanItemType.RESTAURANT, 30L, "제주세호해장국", "한식", date.atTime(11, 14), date.atTime(12, 29), 33.41, 126.41),
                item(TripPlanItemType.AIRPORT, null, "제주국제공항", "RETURN_DEPARTURE_AIRPORT", date.atTime(14, 45), date.atTime(16, 15), 33.51, 126.49)
        ));

        List<TripPlanDayResponse> result = processor.fillLastDayByActualSlack(
                trip,
                pool,
                List.of(day(1, date.minusDays(2), List.of()), day(2, date.minusDays(1), List.of()), lastDay)
        );

        assertThat(result.get(2).items())
                .extracting(TripPlanItemResponse::type)
                .containsSequence(
                        TripPlanItemType.RESTAURANT,
                        TripPlanItemType.CAFE,
                        TripPlanItemType.AIRPORT
                );

        TripPlanItemResponse insertedCafe = result.get(2).items().stream()
                .filter(item -> item.type() == TripPlanItemType.CAFE)
                .findFirst()
                .orElseThrow();
        assertThat(insertedCafe.startAt()).isEqualTo(date.atTime(12, 49));
        assertThat(insertedCafe.endAt()).isEqualTo(date.atTime(13, 34));
    }

    @Test
    void normalizesMealReasonOnlyAfterFinalStartTimeIsKnown() {
        LocalDate date = LocalDate.of(2026, 9, 19);
        TripPlanItemResponse meal = item(
                TripPlanItemType.RESTAURANT,
                30L,
                "제주세호해장국",
                "한식",
                date.atTime(11, 14),
                date.atTime(12, 29),
                33.41,
                126.41
        );

        List<TripPlanDayResponse> result = processor.normalizeMealRoleAfterRouting(
                List.of(day(3, date, List.of(meal)))
        );

        assertThat(result.get(0).items().get(0).reason())
                .contains("점심")
                .doesNotContain("아침");
    }

    private TripPlanCandidatePool pool(
            List<TripPlanCandidatePool.RestaurantCandidate> restaurants,
            List<TripPlanCandidatePool.AttractionCandidate> attractions,
            List<TripPlanCandidatePool.CafeCandidate> cafes
    ) {
        return new TripPlanCandidatePool(
                new TripPlanSelectedAccommodation(
                        1L, "hotel", "숙소", "제주시", 33.5, 126.5, "15:00", "11:00"
                ),
                null,
                attractions,
                restaurants,
                cafes,
                List.of()
        );
    }

    private TripPlanCandidatePool.RestaurantCandidate restaurant(
            Long id,
            String name,
            double breakfastFit,
            double lunchFit,
            double dinnerFit
    ) {
        return new TripPlanCandidatePool.RestaurantCandidate(
                id, name, "한식", 33.5 + id / 1000.0, 126.5 + id / 1000.0,
                1.0, 5, 0.9, List.of(), "", "", "",
                4.7, 1000, "", 0.9,
                breakfastFit, lunchFit, dinnerFit,
                1.0, 5, 1.0, 5
        );
    }

    private TripPlanCandidatePool.AttractionCandidate attraction(Long id, String name) {
        return new TripPlanCandidatePool.AttractionCandidate(
                id, name, "관광", 33.6, 126.6,
                1.0, 5, 0.9, "", "", false,
                1.0, 5, 1.0, 5
        );
    }

    private TripPlanCandidatePool.CafeCandidate cafe(Long id, String name) {
        return new TripPlanCandidatePool.CafeCandidate(
                id, name, "카페", 33.55, 126.55,
                1.0, 5, 0.9, "", "", "",
                4.8, 2000, "", 0.95,
                1.0, 5, 1.0, 5
        );
    }

    private TripPlanDayResponse day(
            int dayNumber,
            LocalDate date,
            List<TripPlanItemResponse> items
    ) {
        return new TripPlanDayResponse(dayNumber, date, items, List.of());
    }

    private TripPlanItemResponse item(
            TripPlanItemType type,
            Long placeId,
            String name,
            String category,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Double latitude,
            Double longitude
    ) {
        Integer stay = startAt != null && endAt != null
                ? (int) java.time.Duration.between(startAt, endAt).toMinutes()
                : null;
        return new TripPlanItemResponse(
                0, type, placeId, null, name, category,
                latitude, longitude, startAt, endAt, stay,
                SegmentTransportMode.RENTAL_CAR,
                "기존 reason"
        );
    }
}
