package com.travel.trip.plan.service;

import com.travel.flight.dto.FlightCandidate;
import com.travel.global.exception.BusinessException;
import com.travel.routing.dto.DrivingRouteResult;
import com.travel.routing.dto.RoutePoint;
import com.travel.routing.service.RoutingService;
import com.travel.trip.dto.TransportSegmentResponse;
import com.travel.trip.entity.*;
import com.travel.trip.plan.dto.*;
import com.travel.trip.plan.type.TripPlanItemType;
import com.travel.trip.repository.TransportSegmentRepository;
import com.travel.trip.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TripPlanFlightTimingTest {
    private final LocalDate date = LocalDate.of(2026, 9, 20);
    private RoutingService routing;
    private TripPlanService service;
    private Trip trip;
    private TripDay tripDay;

    @BeforeEach void setup() {
        routing = mock(RoutingService.class);
        when(routing.findDrivingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new DrivingRouteResult(5000, 1200, 0, 0,
                        List.of(new RoutePoint(33.5, 126.5), new RoutePoint(33.51, 126.51))));
        service = new TripPlanService(mock(TripRepository.class), null,
                mock(TransportSegmentRepository.class), null, null,
                new TripPlanSchedulePostProcessor(routing), routing);
        trip = mock(Trip.class);
        when(trip.getLocalTransportMode()).thenReturn(LocalTransportMode.RENTAL_CAR);
        TripRentalSelection rental = mock(TripRentalSelection.class);
        when(trip.getSelectedRental()).thenReturn(rental);
        when(rental.getCompany()).thenReturn("빌리카");
        when(rental.getLatitude()).thenReturn(33.5041086);
        when(rental.getLongitude()).thenReturn(126.5008161);
        when(rental.getEstimatedShuttleMinutes()).thenReturn(8);
        tripDay = TripDay.builder().trip(trip).dayNumber(3).date(date).build();
        when(trip.getTripDays()).thenReturn(List.of(tripDay));
    }

    @Test void departurePreparationIsNotClampedToTripSearchStartTime() {
        when(trip.getMainTransportMode()).thenReturn(MainTransportMode.AIR);
        when(trip.getStartDate()).thenReturn(date);
        when(trip.getStartTime()).thenReturn(LocalTime.of(10, 30));
        FlightCandidate outbound = new FlightCandidate("out", null, "항공사", "LJ", "LJ507",
                "GMP", "CJU", at("10:30"), at("11:45"), 0, 0, null, null, null);
        List<TripPlanItemResponse> items = new ArrayList<>();
        ReflectionTestUtils.invokeMethod(service, "appendTripStart", trip, outbound, items);
        assertThat(items.getFirst().startAt()).isEqualTo(at("09:00"));
        assertThat(items.get(1).startAt()).isEqualTo(at("10:30"));
        assertThat(items.get(2).startAt()).isEqualTo(at("11:45"));
        assertThat(items.get(2).endAt()).isEqualTo(at("12:15"));
    }

    @Test void removesLateVisitsInsteadOfMovingTheSeventeenOClockFlight() {
        List<TripPlanItemResponse> items = List.of(
                item(TripPlanItemType.ACCOMMODATION, "CHECK_OUT", "11:00", null),
                item(TripPlanItemType.RESTAURANT, "블루그라스", "11:02", "12:17"),
                item(TripPlanItemType.ATTRACTION, "이랜드뮤지엄", "12:24", "13:54"),
                item(TripPlanItemType.RESTAURANT, "차롱보말", "14:41", "15:56"),
                item(TripPlanItemType.CAFE, "소길다방", "16:25", "17:25"),
                item(TripPlanItemType.RESTAURANT, "애월그때그집", "17:53", "19:08"),
                item(TripPlanItemType.AIRPORT, "RETURN_DEPARTURE_AIRPORT", "15:30", "17:00"),
                item(TripPlanItemType.FLIGHT, "CJU → GMP", "17:00", "18:15"));
        List<TripPlanDayResponse> result = enforce(items);
        assertThat(result.getFirst().items()).hasSize(5);
        TripPlanItemResponse airport = result.getFirst().items().get(3);
        assertThat(airport.startAt()).isEqualTo(at("14:42")); // 13:54 + drive20 + return20 + shuttle8
        assertThat(airport.endAt()).isEqualTo(at("17:00"));
        assertThat(result.getFirst().items().getLast().startAt()).isEqualTo(at("17:00"));
        Map<Integer, List<TransportSegmentResponse>> segments = ReflectionTestUtils.invokeMethod(
                service, "rebuildTransportSegments", trip, result, null, null);
        TransportSegmentResponse shuttle = segments.get(3).stream()
                .filter(segment -> segment.mode() == SegmentTransportMode.SHUTTLE).findFirst().orElseThrow();
        assertThat(shuttle.arrivalAt()).isEqualTo(airport.startAt());
    }

    @Test void firstLocalVisitIncludesArrivalProcessingShuttleAndRentalPickup() {
        TripPlanItemResponse airport = item(TripPlanItemType.AIRPORT, "ARRIVAL_AIRPORT", "11:45", "12:15");
        List<TripPlanDayResponse> days = List.of(new TripPlanDayResponse(3, date, List.of(
                airport, item(TripPlanItemType.RESTAURANT, "점심", "12:00", "13:15")), List.of()));
        List<TripPlanDayResponse> aligned = ReflectionTestUtils.invokeMethod(service,
                "reflowPlanTimesWithActualRoutes", trip, days);
        assertThat(aligned.getFirst().items().get(1).startAt()).isEqualTo(at("13:03"));
        Map<Integer, List<TransportSegmentResponse>> segments = ReflectionTestUtils.invokeMethod(
                service, "rebuildTransportSegments", trip, aligned, null, null);
        assertThat(segments.get(3).getFirst().departureAt()).isEqualTo(at("12:15"));
        assertThat(segments.get(3).getLast().departureAt()).isEqualTo(at("12:43"));
        assertThat(segments.get(3).getLast().arrivalAt()).isEqualTo(at("13:03"));
    }

    @Test void allowsEarlyCheckoutForMorningReturnFlight() {
        List<TripPlanDayResponse> result = enforce(List.of(
                item(TripPlanItemType.ACCOMMODATION, "CHECK_OUT", "11:00", null),
                item(TripPlanItemType.AIRPORT, "RETURN_DEPARTURE_AIRPORT", "08:30", "10:00")));
        assertThat(result.getFirst().items().getFirst().startAt()).isEqualTo(at("07:42"));
        assertThat(result.getFirst().items().getLast().startAt()).isEqualTo(at("08:30"));
    }

    @Test void rejectsImpossibleConnectionsInsteadOfSavingOverlappingFlight() {
        assertThatThrownBy(() -> enforce(List.of(
                item(TripPlanItemType.AIRPORT, "ARRIVAL_AIRPORT", "15:00", "15:30"),
                item(TripPlanItemType.AIRPORT, "RETURN_DEPARTURE_AIRPORT", "15:30", "17:00"))))
                .isInstanceOf(BusinessException.class);
    }

    @Test void fallbackTimelineAndSegmentsUseTheSameMinutesAndKeepAnEstimatedPath() {
        when(routing.findDrivingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new IllegalStateException("offline"));
        Long timelineMinutes = ReflectionTestUtils.invokeMethod(service, "timelineRouteMinutes",
                SegmentTransportMode.RENTAL_CAR, 33.4562833, 126.3090639, 33.510413, 126.491353);
        TransportSegment segment = ReflectionTestUtils.invokeMethod(service, "createRoutedSegment",
                tripDay, 1, SegmentTransportMode.RENTAL_CAR, "숙소", "공항",
                33.4562833, 126.3090639, 33.510413, 126.491353, at("12:00"), null);
        assertThat(segment.getDurationMinutes()).isEqualTo(timelineMinutes);
        TransportSegmentResponse response = TransportSegmentResponse.from(segment);
        assertThat(response.path()).hasSize(2);
        assertThat(response.routeProvider()).isEqualTo("ESTIMATED");
    }

    private List<TripPlanDayResponse> enforce(List<TripPlanItemResponse> items) {
        return ReflectionTestUtils.invokeMethod(service, "enforceReturnFlightDeadline", trip,
                List.of(new TripPlanDayResponse(3, date, items, List.of())), true);
    }
    private LocalDateTime at(String time) { return date.atTime(LocalTime.parse(time)); }
    private TripPlanItemResponse item(TripPlanItemType type, String category, String start, String end) {
        return new TripPlanItemResponse(0, type, null, null, category, category, 33.5, 126.5,
                at(start), end == null ? null : at(end),
                end == null ? 0 : (int)Duration.between(at(start), at(end)).toMinutes(),
                type == TripPlanItemType.FLIGHT ? SegmentTransportMode.AIR : SegmentTransportMode.RENTAL_CAR, "");
    }
}
