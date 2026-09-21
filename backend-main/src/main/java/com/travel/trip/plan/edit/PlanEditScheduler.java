package com.travel.trip.plan.edit;

import com.travel.routing.service.RoutingService;
import com.travel.trip.entity.Trip;
import com.travel.trip.entity.SegmentTransportMode;
import com.travel.trip.plan.dto.TripPlanDayResponse;
import com.travel.trip.plan.dto.TripPlanItemResponse;
import com.travel.trip.plan.service.FlightTimePolicy;
import com.travel.trip.plan.type.TripPlanItemType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Component
@RequiredArgsConstructor
public class PlanEditScheduler {
    private final RoutingService routing;
    private final PlanPlaceService places;

    public TripPlanDayResponse schedule(Trip trip, TripPlanDayResponse before, PlanEditRequest.Day draft,
                                        Map<String, TripPlanItemResponse> source) {
        List<TripPlanItemResponse> result = new ArrayList<>();
        for (var spec : draft.items()) {
            var original = source.get(spec.itemKey());
            var current = resolve(original, spec);
            if (result.isEmpty()) {
                result.add(current); // Policy has verified the original start anchor.
                continue;
            }
            var previous = result.getLast();
            if (PlanEditPolicy.editable(current)) {
                validateLocalPosition(previous);
                LocalDateTime earliest = endOf(previous).plusMinutes(travelMinutes(trip, previous, current));
                LocalDateTime desired = spec.startTime() == null ? earliest
                        : before.date().atTime(LocalTime.parse(spec.startTime()));
                LocalDateTime start = desired.isAfter(earliest) ? desired : earliest;
                int stay = spec.stayMinutes() == null ? defaultStay(current) : spec.stayMinutes();
                if (stay < 10 || stay > 240) throw PlanEditException.invalid("체류시간은 10~240분이어야 합니다.");
                LocalDateTime end = start.plusMinutes(stay);
                if (!end.toLocalDate().equals(before.date()) || end.toLocalTime().isAfter(LocalTime.of(23, 0))) {
                    throw PlanEditException.invalid(before.dayNumber() + "일차 일정이 23시를 넘습니다. 장소나 체류시간을 줄여주세요.");
                }
                current = copy(current, result.size() + 1, start, end, stay,
                        "사용자가 변경한 일정입니다. 이동시간을 반영해 시작 시각을 계산했습니다.");
            } else {
                current = alignAnchor(trip, previous, current, before);
                current = copy(current, result.size() + 1, current.startAt(), current.endAt(), current.stayMinutes(), current.reason());
            }
            result.add(current);
        }
        return new TripPlanDayResponse(before.dayNumber(), before.date(), result, List.of());
    }

    private TripPlanItemResponse resolve(TripPlanItemResponse original, PlanEditRequest.Item spec) {
        if (spec.replacement() == null) return Objects.requireNonNull(original);
        var p = places.resolve(spec.replacement());
        return new TripPlanItemResponse(0, p.type(), p.placeId(), null, p.name(), p.category(),
                p.latitude(), p.longitude(), null, null, spec.stayMinutes(), SegmentTransportMode.RENTAL_CAR,
                "사용자가 선택한 장소입니다.");
    }

    private void validateLocalPosition(TripPlanItemResponse previous) {
        if (previous.type() == TripPlanItemType.FLIGHT
                || "DEPARTURE_AIRPORT".equals(previous.category())
                || "RETURN_DEPARTURE_AIRPORT".equals(previous.category())) {
            throw PlanEditException.invalid("탑승 준비·항공·도착 수속 사이에는 관광 일정을 넣을 수 없습니다.");
        }
    }

    private TripPlanItemResponse alignAnchor(Trip trip, TripPlanItemResponse previous,
                                              TripPlanItemResponse anchor, TripPlanDayResponse day) {
        if (anchor.type() == TripPlanItemType.FLIGHT || previous.type() == TripPlanItemType.FLIGHT) {
            if (PlanEditPolicy.editable(previous)) throw PlanEditException.invalid("항공편 직전에는 공항 일정이 필요합니다.");
            return anchor;
        }
        LocalDateTime arrival = endOf(previous).plusMinutes(travelMinutes(trip, previous, anchor));
        if ("NIGHT_RETURN".equals(anchor.category())) {
            if (arrival.isAfter(day.date().atTime(23, 0))) {
                throw PlanEditException.invalid(day.dayNumber() + "일차 숙소 복귀가 23시를 넘습니다.");
            }
            return copy(anchor, anchor.order(), arrival, null, anchor.stayMinutes(), anchor.reason());
        }
        LocalDateTime deadline = anchor.startAt();
        if ("RETURN_DEPARTURE_AIRPORT".equals(anchor.category()) && trip.getReturnFlightCandidate() != null) {
            LocalDateTime flightDeadline = FlightTimePolicy.airportDeadline(trip.getReturnFlightCandidate().departureTime());
            if (deadline == null || flightDeadline.isBefore(deadline)) deadline = flightDeadline;
        }
        if (deadline == null || arrival.isAfter(deadline)) {
            throw PlanEditException.invalid(day.dayNumber() + "일차 '" + anchor.name()
                    + "' 고정 시간에 도착할 수 없습니다. 렌터카 반납·셔틀·공항 여유시간까지 확보해주세요.");
        }
        return anchor;
    }

    long travelMinutes(Trip trip, TripPlanItemResponse from, TripPlanItemResponse to) {
        var rental = trip.getSelectedRental();
        if ("ARRIVAL_AIRPORT".equals(from.category()) && rental != null) {
            return Math.max(0, rental.getEstimatedShuttleMinutes()) + FlightTimePolicy.RENTAL_PICKUP_MINUTES
                    + route(rental.getLatitude(), rental.getLongitude(), to.latitude(), to.longitude());
        }
        if ("RETURN_DEPARTURE_AIRPORT".equals(to.category()) && rental != null) {
            return route(from.latitude(), from.longitude(), rental.getLatitude(), rental.getLongitude())
                    + FlightTimePolicy.RENTAL_RETURN_MINUTES + Math.max(0, rental.getEstimatedShuttleMinutes());
        }
        return route(from.latitude(), from.longitude(), to.latitude(), to.longitude());
    }

    private long route(Double fromLat, Double fromLon, Double toLat, Double toLon) {
        if (!PlanPlaceService.validCoordinates(fromLat, fromLon) || !PlanPlaceService.validCoordinates(toLat, toLon)) {
            throw PlanEditException.invalid("좌표가 없는 일정은 이동시간을 검증할 수 없습니다.");
        }
        if (routing.isSameLocation(fromLat, fromLon, toLat, toLon)) {
            return 0;
        }
        try {
            var result = routing.findDrivingRoute(fromLat, fromLon, toLat, toLon);
            return (long) Math.ceil(result.durationSeconds() / 60.0);
        } catch (RuntimeException e) {
            // Do not save optimistic straight-line estimates as a flight-safe itinerary.
            throw new PlanEditException(HttpStatus.SERVICE_UNAVAILABLE,
                    "이동시간을 조회하지 못해 저장하지 않았습니다. 잠시 후 다시 저장해주세요.");
        }
    }

    private LocalDateTime endOf(TripPlanItemResponse item) {
        var end = item.endAt() == null ? item.startAt() : item.endAt();
        if (end == null) throw PlanEditException.invalid("기존 일정에 기준 시각이 없습니다. 일정을 다시 생성해주세요.");
        return end;
    }

    private int defaultStay(TripPlanItemResponse item) {
        return item.stayMinutes() != null && item.stayMinutes() >= 10 ? item.stayMinutes()
                : item.type() == TripPlanItemType.ATTRACTION ? 60 : 45;
    }

    static TripPlanItemResponse copy(TripPlanItemResponse item, int order, LocalDateTime start,
                                     LocalDateTime end, Integer stay, String reason) {
        return new TripPlanItemResponse(order, item.type(), item.placeId(), item.referenceId(), item.name(),
                item.category(), item.latitude(), item.longitude(), start, end, stay,
                PlanEditPolicy.editable(item) ? SegmentTransportMode.RENTAL_CAR : item.transportModeFromPrevious(), reason);
    }
}
