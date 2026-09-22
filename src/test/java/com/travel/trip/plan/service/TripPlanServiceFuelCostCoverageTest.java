package com.travel.trip.plan.service;

import com.travel.routing.service.RoutingService;
import com.travel.routing.dto.DrivingRouteResult;
import com.travel.routing.dto.RoutePoint;
import com.travel.trip.entity.Trip;
import com.travel.trip.entity.VehicleFuelType;
import com.travel.trip.entity.TripPace;
import com.travel.trip.plan.dto.TripPlanCandidatePool;
import com.travel.trip.plan.dto.TripPlanDayResponse;
import com.travel.trip.plan.dto.TripPlanItemResponse;
import com.travel.trip.plan.dto.TripPlanSelectedAccommodation;
import com.travel.trip.plan.type.TripPlanItemType;
import com.travel.trip.entity.SegmentTransportMode;
import com.travel.trip.repository.TripRepository;
import com.travel.trip.service.FuelCostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripPlanServiceFuelCostCoverageTest {

    private FuelCostService fuelCostService;
    private TripPlanService service;

    @BeforeEach
    void setUp() {
        fuelCostService = mock(FuelCostService.class);
        RoutingService routing = mock(RoutingService.class);
        service = new TripPlanService(
                mock(TripRepository.class), null, null, null, null,
                new TripPlanSchedulePostProcessor(routing), routing, fuelCostService
        );
    }

    @Test
    void rentalAndOwnCarUseFuelCostPlusToll() {
        Trip trip = mock(Trip.class);
        when(trip.getFuelType()).thenReturn(VehicleFuelType.GASOLINE);
        when(trip.getVehicleEfficiencyKmpl()).thenReturn(14.5);
        when(fuelCostService.calculateFuelCost(VehicleFuelType.GASOLINE, 14.5, 100.0))
                .thenReturn(12_000L);

        DrivingRouteResult route = new DrivingRouteResult(
                100_000, 3_600, 20_000L, 5_000L, List.<RoutePoint>of()
        );

        Long rental = ReflectionTestUtils.invokeMethod(
                service, "calculateActualRouteCost",
                SegmentTransportMode.RENTAL_CAR, 100.0, route, trip
        );
        Long ownCar = ReflectionTestUtils.invokeMethod(
                service, "calculateActualRouteCost",
                SegmentTransportMode.OWN_CAR, 100.0, route, trip
        );

        assertThat(rental).isEqualTo(17_000L);
        assertThat(ownCar).isEqualTo(17_000L);
        verify(fuelCostService, org.mockito.Mockito.times(2))
                .calculateFuelCost(VehicleFuelType.GASOLINE, 14.5, 100.0);
    }

    @Test
    void taxiUsesKakaoFareAndTemporaryCarSegmentHasNoFuelCost() {
        DrivingRouteResult route = new DrivingRouteResult(
                10_000, 900, 12_345L, 1_500L, List.<RoutePoint>of()
        );

        Long taxi = ReflectionTestUtils.invokeMethod(
                service, "calculateActualRouteCost",
                SegmentTransportMode.TAXI, 10.0, route, null
        );
        Long temporaryRental = ReflectionTestUtils.invokeMethod(
                service, "calculateActualRouteCost",
                SegmentTransportMode.RENTAL_CAR, 10.0, route, null
        );
        Long walk = ReflectionTestUtils.invokeMethod(
                service, "calculateActualRouteCost",
                SegmentTransportMode.WALK, 10.0, route, null
        );

        assertThat(taxi).isEqualTo(12_345L);
        assertThat(temporaryRental).isZero();
        assertThat(walk).isZero();
    }
    @Test
    void resolvesAndValidatesPromptRequiredAttractionIds() {
        Trip trip = Trip.builder()
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
                .prompt("한담해안산책로는 3일차에 가고 싶어요")
                .build();

        TripPlanCandidatePool pool = new TripPlanCandidatePool(
                new TripPlanSelectedAccommodation(1L, "hotel", "숙소", "제주", 33.45, 126.3, "15:00", "11:00"),
                null,
                List.of(new TripPlanCandidatePool.AttractionCandidate(
                        10L, "한담해안산책로", "관광지", 33.46, 126.31,
                        1.0, 5, 0.9, "", "", false,
                        null, null, null, null
                )),
                List.of(), List.of(), List.of()
        );

        @SuppressWarnings("unchecked")
        Set<Long> day3Ids = ReflectionTestUtils.invokeMethod(
                service, "promptRequiredAttractionIds", trip, pool, 3
        );
        assertThat(day3Ids).containsExactly(10L);

        TripPlanItemResponse item = new TripPlanItemResponse(
                1, TripPlanItemType.ATTRACTION, 10L, null, "한담해안산책로", "관광지",
                33.46, 126.31, LocalDateTime.of(2026, 9, 24, 14, 0),
                LocalDateTime.of(2026, 9, 24, 15, 0), 60, null, "지정 관광지"
        );
        List<TripPlanDayResponse> days = List.of(
                new TripPlanDayResponse(1, LocalDate.of(2026, 9, 22), List.of(), List.of()),
                new TripPlanDayResponse(2, LocalDate.of(2026, 9, 23), List.of(), List.of()),
                new TripPlanDayResponse(3, LocalDate.of(2026, 9, 24), List.of(item), List.of())
        );

        ReflectionTestUtils.invokeMethod(
                service, "validatePromptDayConstraintsPreserved", trip, pool, days
        );
    }

    @Test
    void rejectsFinalScheduleWhenPromptAttractionMovedToWrongDay() {
        Trip trip = Trip.builder()
                .departure("서울").departureLatitude(37.56).departureLongitude(126.97)
                .destination("제주").destinationLatitude(33.49).destinationLongitude(126.53)
                .startDate(LocalDate.of(2026, 9, 22)).startTime(LocalTime.of(9, 0))
                .endDate(LocalDate.of(2026, 9, 24)).endTime(LocalTime.of(18, 0))
                .peopleCount(1).budget(1_000_000L).mealBudgetPerPersonPerDay(40_000L)
                .pace(TripPace.BALANCED).preferences(Set.of()).foodPreferences(Set.of())
                .prompt("한담해안산책로는 3일차").build();

        TripPlanCandidatePool pool = new TripPlanCandidatePool(
                new TripPlanSelectedAccommodation(1L, "hotel", "숙소", "제주", 33.45, 126.3, "15:00", "11:00"),
                null,
                List.of(new TripPlanCandidatePool.AttractionCandidate(
                        10L, "한담해안산책로", "관광지", 33.46, 126.31, 1.0, 5, 0.9, "", "", false,
                        null, null, null, null
                )),
                List.of(), List.of(), List.of()
        );

        TripPlanItemResponse wrongDayItem = new TripPlanItemResponse(
                1, TripPlanItemType.ATTRACTION, 10L, null, "한담해안산책로", "관광지",
                33.46, 126.31, null, null, 60, null, "wrong"
        );
        List<TripPlanDayResponse> days = List.of(
                new TripPlanDayResponse(1, LocalDate.of(2026, 9, 22), List.of(), List.of()),
                new TripPlanDayResponse(2, LocalDate.of(2026, 9, 23), List.of(wrongDayItem), List.of()),
                new TripPlanDayResponse(3, LocalDate.of(2026, 9, 24), List.of(), List.of())
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                ReflectionTestUtils.invokeMethod(
                        service, "validatePromptDayConstraintsPreserved", trip, pool, days
                )
        ).isInstanceOf(IllegalStateException.class);
    }

}
