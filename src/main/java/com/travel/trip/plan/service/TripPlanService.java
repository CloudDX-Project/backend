package com.travel.trip.plan.service;

import com.travel.flight.dto.FlightCandidate;
import com.travel.global.exception.BusinessException;
import com.travel.global.exception.ErrorCode;
import com.travel.global.time.ScheduleTime;
import com.travel.routing.dto.DrivingRouteResult;
import com.travel.routing.dto.RoutePoint;
import com.travel.routing.service.RoutingService;
import com.travel.routing.util.RoutePathCodec;
import com.travel.trip.dto.TransportSegmentResponse;
import com.travel.trip.dto.TripSelectedRentalResponse;
import com.travel.trip.entity.LocalTransportMode;
import com.travel.trip.entity.MainTransportMode;
import com.travel.trip.entity.SegmentTransportMode;
import com.travel.trip.entity.TransportSegment;
import com.travel.trip.entity.Trip;
import com.travel.trip.entity.TripDay;
import com.travel.trip.entity.TripRentalSelection;
import com.travel.trip.plan.dto.TripPlanCandidatePool;
import com.travel.trip.plan.dto.TripPlanDayResponse;
import com.travel.trip.plan.dto.TripPlanItemResponse;
import com.travel.trip.plan.dto.TripPlanResponse;
import com.travel.trip.plan.dto.TripPlanSelectedAccommodation;
import com.travel.trip.plan.entity.TripPlanItem;
import com.travel.trip.plan.repository.TripPlanItemRepository;
import com.travel.trip.plan.type.TripPlanItemType;
import com.travel.trip.repository.TransportSegmentRepository;
import com.travel.trip.repository.TripRepository;
import com.travel.trip.service.FuelCostService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@Transactional(readOnly = true)
public class TripPlanService {

    private static final int AIRPORT_BUFFER_MINUTES = FlightTimePolicy.AIRPORT_BUFFER_MINUTES;

    private final TripRepository tripRepository;
    private final TripPlanItemRepository tripPlanItemRepository;
    private final TransportSegmentRepository transportSegmentRepository;
    private final TripPlanCandidateService candidateService;
    private final TripPlanBedrockService bedrockService;
    private final TripPlanSchedulePostProcessor schedulePostProcessor;
    private final RoutingService routingService;
    private final FuelCostService fuelCostService;

    public TripPlanService(
            TripRepository tripRepository,
            TripPlanItemRepository tripPlanItemRepository,
            TransportSegmentRepository transportSegmentRepository,
            TripPlanCandidateService candidateService,
            TripPlanBedrockService bedrockService,
            TripPlanSchedulePostProcessor schedulePostProcessor,
            RoutingService routingService,
            FuelCostService fuelCostService
    ) {
        this.tripRepository =
                tripRepository;
        this.tripPlanItemRepository =
                tripPlanItemRepository;
        this.transportSegmentRepository =
                transportSegmentRepository;
        this.candidateService =
                candidateService;
        this.bedrockService =
                bedrockService;
        this.schedulePostProcessor = schedulePostProcessor;
        this.routingService =
                routingService;
        this.fuelCostService =
                fuelCostService;
    }

    @Transactional
    public TripPlanResponse createPlan(
            Long userId,
            Long tripId
    ) {
        return routingService.withSnapshot(() -> createPlanWithRouteSnapshot(userId, tripId));
    }

    private TripPlanResponse createPlanWithRouteSnapshot(Long userId, Long tripId) {
        Trip trip =
                tripRepository.findByIdAndUserId(
                                tripId,
                                userId
                        )
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.TRIP_NOT_FOUND
                                        )
                        );

        FlightCandidate outboundFlight =
                trip.getOutboundFlightCandidate();

        FlightCandidate returnFlight =
                trip.getReturnFlightCandidate();

        validatePersistedSelections(
                trip,
                outboundFlight,
                returnFlight
        );

        TripPlanCandidatePool candidatePool =
                candidateService.buildCandidatePool(
                        userId,
                        trip
                );

        TripPlanBedrockService.PlannerResult plannerResult =
                bedrockService.createPlan(
                        trip,
                        candidatePool,
                        outboundFlight,
                        returnFlight
                );

        List<TripPlanDayResponse> days =
                assembleDays(
                        trip,
                        candidatePool,
                        plannerResult.days(),
                        outboundFlight,
                        returnFlight
                );

        days = enforceFirstDaySingleAccommodationAtEnd(days);
        days = removeRedundantConsecutiveAccommodationStops(days);

        /*
         * Bedrock의 startTime은 목표 시각이다.
         * 최종 저장 전 Kakao Mobility 실제 이동시간으로 각 로컬 일정의 도착/시작 시각을
         * 다시 흘려 보내(time reflow) 화면 일정과 TransportSegment가 같은 시간축을 사용하게 한다.
         */
        days = reflowPlanTimesWithActualRoutes(
                trip,
                days
        );

        days = schedulePostProcessor.repairMealSlots(trip, candidatePool, days);
        days = reflowPlanTimesWithActualRoutes(trip, days);
        days = schedulePostProcessor.ensureFirstDayDinner(trip, candidatePool, days);
        days = reflowPlanTimesWithActualRoutes(trip, days);
        days = schedulePostProcessor.fillLongIdleGaps(trip, candidatePool, days);
        days = schedulePostProcessor.releaseNightReturnDeadlines(days);
        days = reflowPlanTimesWithActualRoutes(trip, days);
        days = enforceReturnFlightDeadline(trip, candidatePool, days, false);
        days = schedulePostProcessor.fillLastDayByActualSlack(trip, candidatePool, days);
        days = reflowPlanTimesWithActualRoutes(trip, days);
        days = enforceReturnFlightDeadline(trip, candidatePool, days, true);
        days = enforceFirstDaySingleAccommodationAtEnd(days);
        days = removeRedundantConsecutiveAccommodationStops(days);
        days = schedulePostProcessor.normalizeMealRoleAfterRouting(days);

        validatePromptDayConstraintsPreserved(
                trip,
                candidatePool,
                days
        );

        persistPlanItems(
                trip,
                days
        );

        Map<Integer, List<TransportSegmentResponse>> transportSegmentsByDay =
                rebuildTransportSegments(
                        trip,
                        days,
                        outboundFlight,
                        returnFlight
                );

        days = attachTransportSegments(
                days,
                transportSegmentsByDay
        );

        return new TripPlanResponse(
                trip.getId(),
                plannerResult.planner(),
                "KAKAO_MOBILITY_ROUTING_WITH_FALLBACK",
                trip.getMainTransportMode(),
                trip.getLocalTransportMode(),
                candidatePool.accommodation(),
                TripSelectedRentalResponse.from(
                        trip.getSelectedRental()
                ),
                outboundFlight,
                returnFlight,
                candidatePool.weather(),
                candidatePool.attractions().size(),
                candidatePool.restaurants().size(),
                candidatePool.cafes().size(),
                days
        );
    }

    /** Deterministic user edit: no candidate search or Bedrock call. Changed days only. */
    @Transactional
    public TripPlanResponse persistEditedPlan(Trip trip, TripPlanResponse original,
                                               List<TripPlanDayResponse> changedDays) {
        persistPlanItems(trip, changedDays);
        var segments = rebuildTransportSegments(trip, changedDays, original.outboundFlight(), original.returnFlight());
        Map<Integer, TripPlanDayResponse> changed = new HashMap<>();
        for (var day : attachTransportSegments(changedDays, segments)) changed.put(day.dayNumber(), day);
        var days = original.days().stream().map(day -> changed.getOrDefault(day.dayNumber(), day)).toList();
        return new TripPlanResponse(original.tripId(), original.planner(), "USER_EDIT_KAKAO_VALIDATED",
                original.mainTransportMode(), original.localTransportMode(), original.selectedAccommodation(),
                original.selectedRental(), original.outboundFlight(), original.returnFlight(), original.weather(),
                original.attractionCandidateCount(), original.restaurantCandidateCount(), original.cafeCandidateCount(), days);
    }

    private void validatePersistedSelections(
            Trip trip,
            FlightCandidate outboundFlight,
            FlightCandidate returnFlight
    ) {
        if (trip.getSelectedAccommodation() == null) {
            throw new BusinessException(
                    ErrorCode.TRIP_PLAN_ACCOMMODATION_NOT_FOUND
            );
        }

        if (
                trip.getMainTransportMode()
                        == MainTransportMode.AIR
                        && (outboundFlight == null || returnFlight == null)
        ) {
            throw new BusinessException(
                    ErrorCode.TRIP_PLAN_FLIGHT_SELECTION_REQUIRED
            );
        }

        if (
                trip.getLocalTransportMode()
                        == LocalTransportMode.RENTAL_CAR
                        && trip.getSelectedRental() == null
        ) {
            throw new BusinessException(
                    ErrorCode.TRIP_PLAN_RENTAL_SELECTION_REQUIRED
            );
        }
    }

    private List<TripPlanDayResponse> reflowPlanTimesWithActualRoutes(
            Trip trip,
            List<TripPlanDayResponse> days
    ) {
        List<TripPlanDayResponse> result = new ArrayList<>();

        for (TripPlanDayResponse day : days) {
            List<TripPlanItemResponse> source = day.items();
            List<TripPlanItemResponse> aligned = new ArrayList<>();

            for (int i = 0; i < source.size(); i++) {
                TripPlanItemResponse current = source.get(i);

                if (aligned.isEmpty()) {
                    aligned.add(current);
                    continue;
                }

                TripPlanItemResponse previous = aligned.get(aligned.size() - 1);

                // 항공편과 항공편 직후 도착 공항은 선택한 실제 항공 시각을 그대로 보존한다.
                if (current.type() == TripPlanItemType.FLIGHT
                        || previous.type() == TripPlanItemType.FLIGHT) {
                    aligned.add(current);
                    continue;
                }

                // 귀국편 탑승 공항은 출발 90분 전이라는 고정 anchor를 유지한다.
                if (isReturnDepartureAirport(current)) {
                    aligned.add(current);
                    continue;
                }

                String category = current.category() == null
                        ? ""
                        : current.category();

                // 하루 시작/체크아웃 숙소는 그 자체가 출발 anchor다.
                if (current.type() == TripPlanItemType.ACCOMMODATION
                        && ("DAY_START".equals(category) || "CHECK_OUT".equals(category))) {
                    aligned.add(current);
                    continue;
                }

                LocalDateTime previousEnd = previous.endAt() != null
                        ? previous.endAt()
                        : previous.startAt();

                if (previousEnd == null) {
                    aligned.add(current);
                    continue;
                }

                long travelMinutes = actualTravelMinutesForTimeline(
                        trip,
                        previous,
                        current
                );

                LocalDateTime earliestStart = previousEnd.plusMinutes(travelMinutes);
                LocalDateTime requestedStart = current.startAt();
                LocalDateTime startAt = requestedStart == null
                        ? earliestStart
                        : (requestedStart.isAfter(earliestStart) ? requestedStart : earliestStart);

                int stayMinutes = resolveStayMinutes(current);
                LocalDateTime endAt;

                if (current.type() == TripPlanItemType.ACCOMMODATION
                        && "NIGHT_RETURN".equals(category)) {
                    endAt = null;
                } else if (stayMinutes > 0) {
                    endAt = startAt.plusMinutes(stayMinutes);
                } else {
                    endAt = current.endAt();
                }

                aligned.add(copyWithTimes(current, startAt, endAt, stayMinutes));
            }

            result.add(new TripPlanDayResponse(
                    day.dayNumber(),
                    day.date(),
                    applyOrders(aligned),
                    List.of()
            ));
        }

        return result;
    }

    private long actualTravelMinutesForTimeline(
            Trip trip, TripPlanItemResponse previous, TripPlanItemResponse current
    ) {
        TripRentalSelection rental = trip.getSelectedRental();
        SegmentTransportMode mode = localSegmentMode(trip);
        if (trip.getLocalTransportMode() == LocalTransportMode.RENTAL_CAR && rental != null) {
            if (isArrivalAirport(previous)) {
                return Math.max(0, rental.getEstimatedShuttleMinutes())
                        + FlightTimePolicy.RENTAL_PICKUP_MINUTES
                        + timelineRouteMinutes(mode, rental.getLatitude(), rental.getLongitude(),
                                current.latitude(), current.longitude());
            }
            if (isReturnDepartureAirport(current)) {
                return timelineRouteMinutes(mode, previous.latitude(), previous.longitude(),
                                rental.getLatitude(), rental.getLongitude())
                        + FlightTimePolicy.RENTAL_RETURN_MINUTES
                        + Math.max(0, rental.getEstimatedShuttleMinutes());
            }
        }
        return timelineRouteMinutes(mode, previous.latitude(), previous.longitude(),
                current.latitude(), current.longitude());
    }

    private long timelineRouteMinutes(SegmentTransportMode mode, Double originLatitude,
                                      Double originLongitude, Double destinationLatitude, Double destinationLongitude) {
        if (routingService.isSameLocation(originLatitude, originLongitude,
                destinationLatitude, destinationLongitude)) {
            return 0L;
        }
        // 시간표도 저장되는 이동 구간과 동일한 계산식/조회 결과를 쓴다.
        TransportSegment segment = createRoutedSegment(null, 0, mode, "출발", "도착",
                originLatitude, originLongitude, destinationLatitude, destinationLongitude, null, null);
        if (segment == null) throw new BusinessException(ErrorCode.TRIP_PLAN_AIRPORT_UNREACHABLE);
        return segment.getDurationMinutes();
    }

    /**
     * 기존 단위 테스트/호출 호환용. 프롬프트 보호가 필요 없는 경우의 기존 동작이다.
     */
    private List<TripPlanDayResponse> enforceReturnFlightDeadline(
            Trip trip, List<TripPlanDayResponse> days, boolean useActualArrival
    ) {
        return enforceReturnFlightDeadline(
                trip,
                null,
                days,
                useActualArrival
        );
    }

    /** 항공편은 이동시키지 않는다. 마감 초과 시 선택 일정을 뒤에서부터 제거한다. */
    private List<TripPlanDayResponse> enforceReturnFlightDeadline(
            Trip trip,
            TripPlanCandidatePool candidatePool,
            List<TripPlanDayResponse> days,
            boolean useActualArrival
    ) {
        List<TripPlanDayResponse> result = new ArrayList<>();
        for (TripPlanDayResponse day : days) {
            List<TripPlanItemResponse> items = new ArrayList<>(day.items());
            Set<Long> promptRequiredAttractionIds =
                    candidatePool == null
                            ? Set.of()
                            : promptRequiredAttractionIds(
                            trip,
                            candidatePool,
                            day.dayNumber()
                    );
            int airportIndex = -1;
            for (int i = 0; i < items.size(); i++) {
                if (isReturnDepartureAirport(items.get(i))) { airportIndex = i; break; }
            }
            if (airportIndex < 0) { result.add(day); continue; }
            TripPlanItemResponse airport = items.get(airportIndex);
            LocalDateTime deadline = FlightTimePolicy.airportDeadline(airport.endAt());
            LocalDateTime arrivalAt;
            while (true) {
                if (airportIndex == 0) throw new BusinessException(ErrorCode.TRIP_PLAN_AIRPORT_UNREACHABLE);
                TripPlanItemResponse previous = items.get(airportIndex - 1);
                LocalDateTime previousEnd = previous.endAt() != null ? previous.endAt() : previous.startAt();
                if (previousEnd == null) throw new BusinessException(ErrorCode.TRIP_PLAN_AIRPORT_UNREACHABLE);
                long transferMinutes = actualTravelMinutesForTimeline(trip, previous, airport);
                arrivalAt = previousEnd.plusMinutes(transferMinutes);
                if (FlightTimePolicy.canReachAirport(previousEnd, transferMinutes, airport.endAt())) break;

                int removable = -1;
                for (int i = airportIndex - 1; i >= 0; i--) {
                    TripPlanItemResponse candidate = items.get(i);
                    TripPlanItemType type = candidate.type();

                    if (type == TripPlanItemType.ATTRACTION
                            && candidate.placeId() != null
                            && promptRequiredAttractionIds.contains(candidate.placeId())) {
                        continue;
                    }

                    if (type == TripPlanItemType.ATTRACTION || type == TripPlanItemType.CAFE
                            || type == TripPlanItemType.RESTAURANT) { removable = i; break; }
                }
                if (removable >= 0) {
                    log.info("공항 마감 초과 일정 제외: day={}, place={}, deadline={}",
                            day.dayNumber(), items.get(removable).name(), deadline);
                    items.remove(removable);
                    airportIndex--;
                    continue;
                }
                // 체크아웃은 퇴실 마감이므로 이른 항공편에는 숙소에서 먼저 출발할 수 있다.
                if ("CHECK_OUT".equals(previous.category())) {
                    LocalDateTime earlyCheckout = deadline.minusMinutes(transferMinutes);
                    if (!earlyCheckout.toLocalDate().equals(day.date())) {
                        throw new BusinessException(ErrorCode.TRIP_PLAN_AIRPORT_UNREACHABLE);
                    }
                    items.set(airportIndex - 1, copyWithTimes(previous, earlyCheckout, null, 0));
                    arrivalAt = deadline;
                    break;
                }
                throw new BusinessException(ErrorCode.TRIP_PLAN_AIRPORT_UNREACHABLE);
            }
            LocalDateTime airportStart = useActualArrival ? arrivalAt : deadline;
            items.set(airportIndex, copyWithTimes(airport, airportStart, airport.endAt(),
                    (int) ScheduleTime.minutesBetween(airportStart, airport.endAt())));
            result.add(new TripPlanDayResponse(day.dayNumber(), day.date(), applyOrders(items), List.of()));
        }
        return result;
    }

    private Set<Long> promptRequiredAttractionIds(
            Trip trip,
            TripPlanCandidatePool candidatePool,
            int dayNumber
    ) {
        if (trip.getPrompt() == null || trip.getPrompt().isBlank()) {
            return Set.of();
        }

        int totalDays = (int) ChronoUnit.DAYS.between(
                trip.getStartDate(),
                trip.getEndDate()
        ) + 1;

        List<TripPromptDayConstraintParser.NamedAttraction> candidates =
                candidatePool.attractions().stream()
                        .map(item -> new TripPromptDayConstraintParser.NamedAttraction(
                                item.id(),
                                item.name()
                        ))
                        .toList();

        Set<Long> result = new HashSet<>();
        TripPromptDayConstraintParser.parse(
                        trip.getPrompt(),
                        totalDays,
                        candidates
                ).stream()
                .filter(item -> item.dayNumber() == dayNumber)
                .map(TripPromptDayConstraintParser.DayConstraint::attractionId)
                .forEach(result::add);

        return result;
    }

    private void validatePromptDayConstraintsPreserved(
            Trip trip,
            TripPlanCandidatePool candidatePool,
            List<TripPlanDayResponse> days
    ) {
        if (trip.getPrompt() == null || trip.getPrompt().isBlank()) {
            return;
        }

        int totalDays = (int) ChronoUnit.DAYS.between(
                trip.getStartDate(),
                trip.getEndDate()
        ) + 1;

        List<TripPromptDayConstraintParser.NamedAttraction> candidates =
                candidatePool.attractions().stream()
                        .map(item -> new TripPromptDayConstraintParser.NamedAttraction(
                                item.id(),
                                item.name()
                        ))
                        .toList();

        for (TripPromptDayConstraintParser.DayConstraint constraint
                : TripPromptDayConstraintParser.parse(
                trip.getPrompt(),
                totalDays,
                candidates
        )) {
            long totalOccurrences = days.stream()
                    .flatMap(day -> day.items().stream())
                    .filter(item ->
                            item.type() == TripPlanItemType.ATTRACTION
                                    && constraint.attractionId().equals(item.placeId())
                    )
                    .count();

            long requestedDayOccurrences = days.stream()
                    .filter(day -> day.dayNumber() == constraint.dayNumber())
                    .flatMap(day -> day.items().stream())
                    .filter(item ->
                            item.type() == TripPlanItemType.ATTRACTION
                                    && constraint.attractionId().equals(item.placeId())
                    )
                    .count();

            if (totalOccurrences != 1L || requestedDayOccurrences != 1L) {
                throw new IllegalStateException(
                        "최종 일정에서 프롬프트 관광지 일차 제약이 정확히 유지되지 않았습니다: "
                                + constraint.attractionName()
                                + " -> "
                                + constraint.dayNumber()
                                + "일차, totalOccurrences="
                                + totalOccurrences
                                + ", requestedDayOccurrences="
                                + requestedDayOccurrences
                );
            }
        }
    }

    private int resolveStayMinutes(TripPlanItemResponse item) {
        if (item.stayMinutes() != null && item.stayMinutes() > 0) {
            return item.stayMinutes();
        }

        if (item.startAt() != null
                && item.endAt() != null
                && item.endAt().isAfter(item.startAt())) {
            long minutes = ScheduleTime.minutesBetween(item.startAt(), item.endAt());
            if (minutes > 0 && minutes <= 240) {
                return (int) minutes;
            }
        }

        if (item.type() == TripPlanItemType.ACCOMMODATION
                && "CHECK_IN".equals(item.category())) {
            return 30;
        }

        return switch (item.type()) {
            case ATTRACTION -> 90;
            case RESTAURANT -> 75;
            case CAFE -> 60;
            default -> 0;
        };
    }

    private TripPlanItemResponse copyWithTimes(
            TripPlanItemResponse item,
            LocalDateTime startAt,
            LocalDateTime endAt,
            int stayMinutes
    ) {

        Integer effectiveStayMinutes =
                stayMinutes > 0
                        ? Integer.valueOf(stayMinutes)
                        : item.stayMinutes();

        return new TripPlanItemResponse(
                item.order(),
                item.type(),
                item.placeId(),
                item.referenceId(),
                item.name(),
                item.category(),
                item.latitude(),
                item.longitude(),
                startAt,
                endAt,
                effectiveStayMinutes,
                item.transportModeFromPrevious(),
                item.reason()
        );
    }

    private void persistPlanItems(
            Trip trip,
            List<TripPlanDayResponse> days
    ) {
        Map<Integer, TripDay> tripDayMap =
                new HashMap<>();

        for (TripDay tripDay : trip.getTripDays()) {
            if (days.stream().noneMatch(day -> java.util.Objects.equals(day.dayNumber(), tripDay.getDayNumber()))) continue;
            tripDay.clearPlanItems();
            tripDayMap.put(
                    tripDay.getDayNumber(),
                    tripDay
            );
        }

        /*
         * orphanRemoval DELETE를 먼저 DB에 반영해
         * (trip_day_id, item_order) unique 충돌을 방지한다.
         */
        tripRepository.flush();

        List<TripPlanItem> newItems =
                new ArrayList<>();

        for (TripPlanDayResponse day : days) {
            TripDay tripDay =
                    tripDayMap.get(
                            day.dayNumber()
                    );

            if (tripDay == null) {
                continue;
            }

            for (TripPlanItemResponse item : day.items()) {
                TripPlanItem entity =
                        TripPlanItem.from(
                                tripDay,
                                item
                        );

                tripDay.addPlanItem(entity);
                newItems.add(entity);
            }
        }

        tripPlanItemRepository.saveAll(
                newItems
        );

        tripPlanItemRepository.flush();
    }

    private Map<Integer, List<TransportSegmentResponse>> rebuildTransportSegments(
            Trip trip,
            List<TripPlanDayResponse> days,
            FlightCandidate outboundFlight,
            FlightCandidate returnFlight
    ) {
        Map<Integer, TripDay> tripDayMap =
                new HashMap<>();

        for (TripDay tripDay : trip.getTripDays()) {
            if (days.stream().noneMatch(day -> java.util.Objects.equals(day.dayNumber(), tripDay.getDayNumber()))) continue;
            tripDay.clearTransportSegments();
            tripDayMap.put(
                    tripDay.getDayNumber(),
                    tripDay
            );
        }

        tripRepository.flush();

        List<TransportSegment> newSegments =
                new ArrayList<>();

        TripRentalSelection rental =
                trip.getSelectedRental();

        for (TripPlanDayResponse day : days) {
            TripDay tripDay =
                    tripDayMap.get(
                            day.dayNumber()
                    );

            if (tripDay == null) {
                continue;
            }

            int sequence = 1;

            List<TripPlanItemResponse> items =
                    day.items();

            for (int i = 1; i < items.size(); i++) {
                TripPlanItemResponse previous =
                        items.get(i - 1);

                TripPlanItemResponse current =
                        items.get(i);

                /*
                 * AIRPORT -> FLIGHT -> AIRPORT는 FLIGHT 카드 자체가 항공 이동을
                 * 의미한다. 비행 거리는 별도 추정하지 않고 실제 선택 항공편의
                 * 출/도착 시각만 TransportSegment로 보존한다.
                 */
                if (
                        current.type() == TripPlanItemType.FLIGHT
                                && current.transportModeFromPrevious()
                                == SegmentTransportMode.AIR
                ) {
                    TripPlanItemResponse next =
                            i + 1 < items.size()
                                    ? items.get(i + 1)
                                    : null;

                    FlightCandidate selectedFlight =
                            resolveFlightCandidate(
                                    current,
                                    outboundFlight,
                                    returnFlight
                            );

                    if (previous.type() == TripPlanItemType.AIRPORT) {
                        String arrivalName = null;
                        Double arrivalLatitude = null;
                        Double arrivalLongitude = null;

                        if (
                                next != null
                                        && next.type() == TripPlanItemType.AIRPORT
                        ) {
                            arrivalName = next.name();
                            arrivalLatitude = next.latitude();
                            arrivalLongitude = next.longitude();
                        } else if (selectedFlight != null) {
                            AirportInfo arrivalAirport =
                                    airportInfo(
                                            selectedFlight.arrivalAirport()
                                    );

                            arrivalName = arrivalAirport.name();
                            arrivalLatitude = arrivalAirport.latitude();
                            arrivalLongitude = arrivalAirport.longitude();
                        }

                        if (arrivalName != null) {
                            long durationMinutes =
                                    flightDurationMinutes(
                                            current.startAt(),
                                            current.endAt()
                                    );

                            TransportSegment segment =
                                    TransportSegment.builder()
                                            .tripDay(tripDay)
                                            .sequence(sequence++)
                                            .mode(SegmentTransportMode.AIR)
                                            .departureName(previous.name())
                                            .arrivalName(arrivalName)
                                            .departureLatitude(previous.latitude())
                                            .departureLongitude(previous.longitude())
                                            .arrivalLatitude(arrivalLatitude)
                                            .arrivalLongitude(arrivalLongitude)
                                            .departureAt(current.startAt())
                                            .arrivalAt(current.endAt())
                                            .distanceKm(null)
                                            .durationMinutes(durationMinutes)
                                            .cost(0L)
                                            .routeProvider("FLIGHT")
                                            .routePathJson(null)
                                            .build();

                            tripDay.addTransportSegment(segment);
                            newSegments.add(segment);
                        }
                    }

                    continue;
                }

                /*
                 * FLIGHT 다음 AIRPORT는 위 항공 세그먼트에서 이미 처리했다.
                 */
                if (previous.type() == TripPlanItemType.FLIGHT) {
                    continue;
                }

                SegmentTransportMode mode =
                        current.transportModeFromPrevious();

                if (mode == null || mode == SegmentTransportMode.AIR) {
                    continue;
                }

                /*
                 * 제주 도착 후 렌터카를 이용하는 경우:
                 * 공항 -> 렌터카 업체는 SHUTTLE,
                 * 렌터카 업체 -> 첫 일정은 RENTAL_CAR로 분리한다.
                 */
                if (
                        mode == SegmentTransportMode.RENTAL_CAR
                                && isArrivalAirport(previous)
                                && rental != null
                ) {
                    LocalDateTime shuttleDepartureAt =
                            previous.endAt() != null
                                    ? previous.endAt()
                                    : previous.startAt();

                    LocalDateTime shuttleArrivalAt =
                            shuttleDepartureAt == null
                                    ? null
                                    : shuttleDepartureAt.plusMinutes(
                                    rental.getEstimatedShuttleMinutes()
                            );

                    TransportSegment shuttle =
                            createFixedDurationSegment(
                                    tripDay,
                                    sequence++,
                                    SegmentTransportMode.SHUTTLE,
                                    previous.name(),
                                    rental.getCompany(),
                                    previous.latitude(),
                                    previous.longitude(),
                                    rental.getLatitude(),
                                    rental.getLongitude(),
                                    shuttleDepartureAt,
                                    shuttleArrivalAt,
                                    rental.getEstimatedShuttleMinutes()
                            );

                    tripDay.addTransportSegment(shuttle);
                    newSegments.add(shuttle);

                    TransportSegment localRoute =
                            createRoutedSegment(
                                    tripDay,
                                    sequence,
                                    SegmentTransportMode.RENTAL_CAR,
                                    rental.getCompany(),
                                    current.name(),
                                    rental.getLatitude(),
                                    rental.getLongitude(),
                                    current.latitude(),
                                    current.longitude(),
                                    shuttleArrivalAt == null ? null
                                            : shuttleArrivalAt.plusMinutes(FlightTimePolicy.RENTAL_PICKUP_MINUTES),
                                    current.startAt()
                            );

                    if (localRoute != null) {
                        sequence++;
                        tripDay.addTransportSegment(localRoute);
                        newSegments.add(localRoute);
                    }

                    continue;
                }

                /*
                 * 마지막 일정 -> 렌터카 업체 -> 출발 공항.
                 * 공항까지 렌터카로 직접 가는 것으로 계산하지 않는다.
                 */
                if (
                        mode == SegmentTransportMode.RENTAL_CAR
                                && isReturnDepartureAirport(current)
                                && rental != null
                ) {
                    LocalDateTime routeDepartureAt =
                            previous.endAt() != null
                                    ? previous.endAt()
                                    : previous.startAt();

                    TransportSegment localRoute =
                            createRoutedSegment(
                                    tripDay,
                                    sequence,
                                    SegmentTransportMode.RENTAL_CAR,
                                    previous.name(),
                                    rental.getCompany(),
                                    previous.latitude(),
                                    previous.longitude(),
                                    rental.getLatitude(),
                                    rental.getLongitude(),
                                    routeDepartureAt,
                                    null
                            );

                    LocalDateTime rentalArrivalAt =
                            localRoute == null
                                    ? routeDepartureAt
                                    : localRoute.getArrivalAt();

                    if (localRoute != null) {
                        sequence++;
                        tripDay.addTransportSegment(localRoute);
                        newSegments.add(localRoute);
                    }

                    LocalDateTime shuttleDepartureAt = rentalArrivalAt == null ? null
                            : rentalArrivalAt.plusMinutes(FlightTimePolicy.RENTAL_RETURN_MINUTES);
                    LocalDateTime shuttleArrivalAt =
                            shuttleDepartureAt == null
                                    ? current.startAt()
                                    : shuttleDepartureAt.plusMinutes(
                                    rental.getEstimatedShuttleMinutes()
                            );

                    TransportSegment shuttle =
                            createFixedDurationSegment(
                                    tripDay,
                                    sequence++,
                                    SegmentTransportMode.SHUTTLE,
                                    rental.getCompany(),
                                    current.name(),
                                    rental.getLatitude(),
                                    rental.getLongitude(),
                                    current.latitude(),
                                    current.longitude(),
                                    shuttleDepartureAt,
                                    shuttleArrivalAt,
                                    rental.getEstimatedShuttleMinutes()
                            );

                    tripDay.addTransportSegment(shuttle);
                    newSegments.add(shuttle);
                    continue;
                }

                TransportSegment route =
                        createRoutedSegment(
                                tripDay,
                                sequence,
                                mode,
                                previous.name(),
                                current.name(),
                                previous.latitude(),
                                previous.longitude(),
                                current.latitude(),
                                current.longitude(),
                                previous.endAt() != null
                                        ? previous.endAt()
                                        : previous.startAt(),
                                current.startAt()
                        );

                if (route != null) {
                    sequence++;
                    tripDay.addTransportSegment(route);
                    newSegments.add(route);
                }
            }
        }

        transportSegmentRepository.saveAll(
                newSegments
        );

        transportSegmentRepository.flush();

        Map<Integer, List<TransportSegmentResponse>> result =
                new HashMap<>();

        for (TransportSegment segment : newSegments) {
            result.computeIfAbsent(
                            segment.getTripDay().getDayNumber(),
                            ignored -> new ArrayList<>()
                    )
                    .add(
                            TransportSegmentResponse.from(segment)
                    );
        }

        return result;
    }

    private List<TripPlanDayResponse> attachTransportSegments(
            List<TripPlanDayResponse> days,
            Map<Integer, List<TransportSegmentResponse>> transportSegmentsByDay
    ) {
        List<TripPlanDayResponse> result =
                new ArrayList<>();

        for (TripPlanDayResponse day : days) {
            result.add(
                    new TripPlanDayResponse(
                            day.dayNumber(),
                            day.date(),
                            day.items(),
                            transportSegmentsByDay.getOrDefault(
                                    day.dayNumber(),
                                    List.of()
                            )
                    )
            );
        }

        return result;
    }

    /**
     * CHECK_IN 직후 같은 숙소의 NIGHT_RETURN이 붙는 등 의미 없는 중복 숙소 일정을 제거한다.
     * 이름의 공백/기호 차이와 5m 이내 좌표도 같은 숙소로 취급한다.
     */
    private List<TripPlanDayResponse> removeRedundantConsecutiveAccommodationStops(
            List<TripPlanDayResponse> days
    ) {
        List<TripPlanDayResponse> result = new ArrayList<>();
        for (TripPlanDayResponse day : days) {
            List<TripPlanItemResponse> items = new ArrayList<>();
            for (TripPlanItemResponse current : day.items()) {
                if (!items.isEmpty()
                        && isSameAccommodation(items.get(items.size() - 1), current)) {
                    continue;
                }
                items.add(current);
            }
            result.add(new TripPlanDayResponse(
                    day.dayNumber(), day.date(), applyOrders(items), List.of()
            ));
        }
        return result;
    }

    /**
     * 첫날은 체크인을 위해 숙소를 중간 방문했다가 곧바로 다시 나오는 동선을 만들지 않는다.
     * 숙소 일정이 여러 개 들어와도 마지막 숙소 도착 한 건만 남겨 하루의 마지막 일정으로 사용한다.
     */
    List<TripPlanDayResponse> enforceFirstDaySingleAccommodationAtEnd(
            List<TripPlanDayResponse> days
    ) {
        if (days.isEmpty()) {
            return days;
        }

        TripPlanDayResponse firstDay = days.get(0);
        TripPlanItemResponse finalAccommodation = null;
        List<TripPlanItemResponse> normalizedItems = new ArrayList<>();

        for (TripPlanItemResponse item : firstDay.items()) {
            if (item.type() == TripPlanItemType.ACCOMMODATION) {
                finalAccommodation = item;
            } else {
                normalizedItems.add(item);
            }
        }

        if (finalAccommodation == null) {
            return days;
        }

        normalizedItems.add(finalAccommodation);
        List<TripPlanDayResponse> result = new ArrayList<>(days);
        result.set(0, new TripPlanDayResponse(
                firstDay.dayNumber(),
                firstDay.date(),
                applyOrders(normalizedItems),
                List.of()
        ));
        return result;
    }

    private boolean isSameAccommodation(
            TripPlanItemResponse previous,
            TripPlanItemResponse current
    ) {
        if (previous.type() != TripPlanItemType.ACCOMMODATION
                || current.type() != TripPlanItemType.ACCOMMODATION) {
            return false;
        }
        if (previous.placeId() != null && previous.placeId().equals(current.placeId())) {
            return true;
        }
        String previousName = normalizePlaceName(previous.name());
        String currentName = normalizePlaceName(current.name());
        if (!previousName.isEmpty() && previousName.equals(currentName)) {
            return true;
        }
        return routingService.isSameLocation(
                previous.latitude(), previous.longitude(),
                current.latitude(), current.longitude()
        );
    }

    private String normalizePlaceName(String name) {
        return name == null ? "" : name.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^가-힣a-z0-9]", "");
    }

    private TransportSegment createRoutedSegment(
            TripDay tripDay,
            int sequence,
            SegmentTransportMode mode,
            String departureName,
            String arrivalName,
            Double departureLatitude,
            Double departureLongitude,
            Double arrivalLatitude,
            Double arrivalLongitude,
            LocalDateTime departureAt,
            LocalDateTime preferredArrivalAt
    ) {
        if (
                departureLatitude == null
                        || departureLongitude == null
                        || arrivalLatitude == null
                        || arrivalLongitude == null
        ) {
            return null;
        }

        if (routingService.isSameLocation(departureLatitude, departureLongitude,
                arrivalLatitude, arrivalLongitude)) {
            return null;
        }

        if (isKakaoDrivingMode(mode)) {
            try {
                DrivingRouteResult route =
                        routingService.findDrivingRoute(
                                departureLatitude,
                                departureLongitude,
                                arrivalLatitude,
                                arrivalLongitude
                        );

                double distanceKm =
                        roundOneDecimal(
                                route.distanceMeters() / 1000.0
                        );

                long durationMinutes =
                        Math.max(
                                1L,
                                (long) Math.ceil(
                                        route.durationSeconds() / 60.0
                                )
                        );

                long cost =
                        calculateActualRouteCost(
                                mode,
                                distanceKm,
                                route,
                                tripDay == null ? null : tripDay.getTrip()
                        );

                LocalDateTime arrivalAt =
                        departureAt == null
                                ? preferredArrivalAt
                                : departureAt.plusMinutes(
                                durationMinutes
                        );

                return TransportSegment.builder()
                        .tripDay(tripDay)
                        .sequence(sequence)
                        .mode(mode)
                        .departureName(departureName)
                        .arrivalName(arrivalName)
                        .departureLatitude(departureLatitude)
                        .departureLongitude(departureLongitude)
                        .arrivalLatitude(arrivalLatitude)
                        .arrivalLongitude(arrivalLongitude)
                        .departureAt(departureAt)
                        .arrivalAt(arrivalAt)
                        .distanceKm(distanceKm)
                        .durationMinutes(durationMinutes)
                        .cost(cost)
                        .routeProvider("KAKAO_MOBILITY")
                        .routePathJson(
                                RoutePathCodec.encode(
                                        route.path()
                                )
                        )
                        .build();

            } catch (RuntimeException e) {
                log.warn(
                        "Kakao Mobility 길찾기 실패. 추정 경로로 대체합니다. {} -> {}: {}",
                        departureName,
                        arrivalName,
                        e.getMessage()
                );
            }
        }

        /*
         * Kakao 자동차 길찾기를 사용할 수 없는 모드이거나
         * 외부 API 호출에 실패한 경우 기존 추정식을 fallback으로 사용한다.
         */
        double straightDistanceKm =
                haversineKm(
                        departureLatitude,
                        departureLongitude,
                        arrivalLatitude,
                        arrivalLongitude
                );

        double distanceKm =
                roundOneDecimal(
                        straightDistanceKm
                                * roadDistanceFactor(mode)
                );

        long durationMinutes =
                Math.max(
                        1L,
                        Math.round(
                                distanceKm
                                        / averageSpeedKmh(mode)
                                        * 60.0
                        )
                );

        long cost = 0L;

        if (
                tripDay != null
                        && (mode == SegmentTransportMode.RENTAL_CAR
                        || mode == SegmentTransportMode.OWN_CAR)
        ) {
            Trip trip = tripDay.getTrip();
            cost = fuelCostService.calculateFuelCost(
                    trip == null ? null : trip.getFuelType(),
                    trip == null ? null : trip.getVehicleEfficiencyKmpl(),
                    distanceKm
            );
        }

        LocalDateTime arrivalAt =
                departureAt == null
                        ? preferredArrivalAt
                        : departureAt.plusMinutes(
                        durationMinutes
                );

        return TransportSegment.builder()
                .tripDay(tripDay)
                .sequence(sequence)
                .mode(mode)
                .departureName(departureName)
                .arrivalName(arrivalName)
                .departureLatitude(departureLatitude)
                .departureLongitude(departureLongitude)
                .arrivalLatitude(arrivalLatitude)
                .arrivalLongitude(arrivalLongitude)
                .departureAt(departureAt)
                .arrivalAt(arrivalAt)
                .distanceKm(distanceKm)
                .durationMinutes(durationMinutes)
                .cost(cost)
                .routeProvider("ESTIMATED")
                .routePathJson(RoutePathCodec.encode(List.of(
                        new RoutePoint(departureLatitude, departureLongitude),
                        new RoutePoint(arrivalLatitude, arrivalLongitude))))
                .build();
    }

    private TransportSegment createFixedDurationSegment(
            TripDay tripDay,
            int sequence,
            SegmentTransportMode mode,
            String departureName,
            String arrivalName,
            Double departureLatitude,
            Double departureLongitude,
            Double arrivalLatitude,
            Double arrivalLongitude,
            LocalDateTime departureAt,
            LocalDateTime arrivalAt,
            long durationMinutes
    ) {
        Double distanceKm = null;
        String routeProvider = "FIXED";
        String routePathJson = null;

        /*
         * 셔틀 시간은 업체가 제공한 검증값을 유지하되,
         * 지도 Polyline을 위해 도로 형상만 Kakao에서 받아온다.
         */
        if (
                mode == SegmentTransportMode.SHUTTLE
                        && departureLatitude != null
                        && departureLongitude != null
                        && arrivalLatitude != null
                        && arrivalLongitude != null
        ) {
            try {
                DrivingRouteResult route =
                        routingService.findDrivingRoute(
                                departureLatitude,
                                departureLongitude,
                                arrivalLatitude,
                                arrivalLongitude
                        );

                distanceKm =
                        roundOneDecimal(
                                route.distanceMeters() / 1000.0
                        );

                routeProvider =
                        "KAKAO_MOBILITY_FIXED_TIME";

                routePathJson =
                        RoutePathCodec.encode(
                                route.path()
                        );

            } catch (RuntimeException e) {
                log.warn(
                        "Kakao Mobility 셔틀 경로 형상 조회 실패. {} -> {}: {}",
                        departureName,
                        arrivalName,
                        e.getMessage()
                );
            }
        }

        if (routePathJson == null && departureLatitude != null && departureLongitude != null
                && arrivalLatitude != null && arrivalLongitude != null) {
            routeProvider = "FIXED";
            routePathJson = RoutePathCodec.encode(List.of(
                    new RoutePoint(departureLatitude, departureLongitude),
                    new RoutePoint(arrivalLatitude, arrivalLongitude)));
        }
        return TransportSegment.builder()
                .tripDay(tripDay)
                .sequence(sequence)
                .mode(mode)
                .departureName(departureName)
                .arrivalName(arrivalName)
                .departureLatitude(departureLatitude)
                .departureLongitude(departureLongitude)
                .arrivalLatitude(arrivalLatitude)
                .arrivalLongitude(arrivalLongitude)
                .departureAt(departureAt)
                .arrivalAt(arrivalAt)
                .distanceKm(distanceKm)
                .durationMinutes(durationMinutes)
                .cost(0L)
                .routeProvider(routeProvider)
                .routePathJson(routePathJson)
                .build();
    }

    private boolean isKakaoDrivingMode(
            SegmentTransportMode mode
    ) {
        return mode == SegmentTransportMode.RENTAL_CAR
                || mode == SegmentTransportMode.OWN_CAR
                || mode == SegmentTransportMode.TAXI;
    }

    private long calculateActualRouteCost(
            SegmentTransportMode mode,
            double distanceKm,
            DrivingRouteResult route,
            Trip trip
    ) {
        if (mode == SegmentTransportMode.TAXI) {
            return route.taxiFare();
        }

        if (
                mode == SegmentTransportMode.RENTAL_CAR
                        || mode == SegmentTransportMode.OWN_CAR
        ) {
            /*
             * 시간표 계산용 임시 segment(trip == null)는 비용이 필요 없다.
             * 실제 저장 segment에서만 오피넷 유가를 반영한다.
             */
            if (trip == null) {
                return 0L;
            }

            long fuelCost = fuelCostService.calculateFuelCost(
                    trip.getFuelType(),
                    trip.getVehicleEfficiencyKmpl(),
                    distanceKm
            );

            return fuelCost + route.tollFare();
        }

        return 0L;
    }

    private FlightCandidate resolveFlightCandidate(
            TripPlanItemResponse flightItem,
            FlightCandidate outboundFlight,
            FlightCandidate returnFlight
    ) {
        if (flightItem.referenceId() != null) {
            if (
                    outboundFlight != null
                            && flightItem.referenceId().equals(
                            outboundFlight.id()
                    )
            ) {
                return outboundFlight;
            }

            if (
                    returnFlight != null
                            && flightItem.referenceId().equals(
                            returnFlight.id()
                    )
            ) {
                return returnFlight;
            }
        }

        if (
                outboundFlight != null
                        && flightItem.startAt() != null
                        && flightItem.startAt().equals(
                        outboundFlight.departureTime()
                )
        ) {
            return outboundFlight;
        }

        if (
                returnFlight != null
                        && flightItem.startAt() != null
                        && flightItem.startAt().equals(
                        returnFlight.departureTime()
                )
        ) {
            return returnFlight;
        }

        return null;
    }

    private long flightDurationMinutes(
            LocalDateTime departureAt,
            LocalDateTime arrivalAt
    ) {
        if (departureAt == null || arrivalAt == null) {
            return 0L;
        }

        return Math.max(
                0L,
                ScheduleTime.minutesBetween(departureAt, arrivalAt)
        );
    }

    private boolean isArrivalAirport(
            TripPlanItemResponse item
    ) {
        return item.type() == TripPlanItemType.AIRPORT
                && "ARRIVAL_AIRPORT".equals(
                item.category()
        );
    }

    private boolean isReturnDepartureAirport(
            TripPlanItemResponse item
    ) {
        return item.type() == TripPlanItemType.AIRPORT
                && "RETURN_DEPARTURE_AIRPORT".equals(
                item.category()
        );
    }

    private boolean hasCoordinates(
            TripPlanItemResponse item
    ) {
        return item.latitude() != null
                && item.longitude() != null;
    }

    private double roadDistanceFactor(
            SegmentTransportMode mode
    ) {
        return switch (mode) {
            case WALK -> 1.10;
            case RENTAL_CAR, OWN_CAR, TAXI, PUBLIC_TRANSIT, SHUTTLE -> 1.25;
            case AIR, KTX, SRT, EXPRESS_BUS -> 1.0;
        };
    }

    private double averageSpeedKmh(
            SegmentTransportMode mode
    ) {
        return switch (mode) {
            case WALK -> 4.5;
            case PUBLIC_TRANSIT, SHUTTLE -> 30.0;
            case RENTAL_CAR, OWN_CAR, TAXI -> 35.0;
            case KTX, SRT -> 180.0;
            case EXPRESS_BUS -> 70.0;
            case AIR -> 500.0;
        };
    }

    private double haversineKm(
            double lat1,
            double lon1,
            double lat2,
            double lon2
    ) {
        final double earthRadiusKm =
                6371.0088;

        double dLat =
                Math.toRadians(lat2 - lat1);

        double dLon =
                Math.toRadians(lon2 - lon1);

        double a =
                Math.sin(dLat / 2.0)
                        * Math.sin(dLat / 2.0)
                        + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2.0)
                        * Math.sin(dLon / 2.0);

        double c =
                2.0
                        * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(1.0 - a)
                );

        return earthRadiusKm * c;
    }

    private double roundOneDecimal(
            double value
    ) {
        return Math.round(value * 10.0) / 10.0;
    }

    private List<TripPlanDayResponse> assembleDays(
            Trip trip,
            TripPlanCandidatePool candidatePool,
            List<TripPlanBedrockService.PlannedDay> plannedDays,
            FlightCandidate outboundFlight,
            FlightCandidate returnFlight
    ) {

        Map<Integer, TripPlanBedrockService.PlannedDay> dayMap =
                new HashMap<>();

        for (TripPlanBedrockService.PlannedDay day : plannedDays) {
            dayMap.put(
                    day.dayNumber(),
                    day
            );
        }

        Map<Long, TripPlanCandidatePool.AttractionCandidate> attractionMap =
                new HashMap<>();

        candidatePool.attractions()
                .forEach(
                        item ->
                                attractionMap.put(
                                        item.id(),
                                        item
                                )
                );

        Map<Long, TripPlanCandidatePool.RestaurantCandidate> restaurantMap =
                new HashMap<>();

        candidatePool.restaurants()
                .forEach(
                        item ->
                                restaurantMap.put(
                                        item.id(),
                                        item
                                )
                );

        Map<Long, TripPlanCandidatePool.CafeCandidate> cafeMap =
                new HashMap<>();

        candidatePool.cafes()
                .forEach(
                        item ->
                                cafeMap.put(
                                        item.id(),
                                        item
                                )
                );

        int totalDays =
                (int) ChronoUnit.DAYS.between(
                        trip.getStartDate(),
                        trip.getEndDate()
                ) + 1;

        List<TripPlanDayResponse> result =
                new ArrayList<>();

        for (int dayNumber = 1;
             dayNumber <= totalDays;
             dayNumber++) {

            LocalDate date =
                    trip.getStartDate()
                            .plusDays(
                                    dayNumber - 1L
                            );

            List<TripPlanItemResponse> items =
                    new ArrayList<>();

            boolean firstDay =
                    dayNumber == 1;

            boolean lastDay =
                    dayNumber == totalDays;

            if (firstDay) {
                appendTripStart(
                        trip,
                        outboundFlight,
                        items
                );
            }

            if (!firstDay) {
                LocalTime accommodationDepartureTime =
                        lastDay
                                ? parseAccommodationTime(
                                candidatePool.accommodation().checkOutTime(),
                                LocalTime.of(10, 0)
                        )
                                : plannedDayStartTime(
                                dayMap.get(dayNumber),
                                LocalTime.of(8, 30)
                        ).minusMinutes(30);

                items.add(
                        accommodationItemWithCategory(
                                candidatePool.accommodation(),
                                date,
                                accommodationDepartureTime,
                                null,
                                lastDay ? "CHECK_OUT" : "DAY_START",
                                lastDay
                                        ? "숙소 체크아웃 시간에 맞춰 마지막 날 일정을 시작합니다."
                                        : "숙소에서 하루 일정을 시작합니다."
                        )
                );
            }

            TripPlanBedrockService.PlannedDay plannedDay =
                    dayMap.get(
                            dayNumber
                    );

            if (plannedDay != null) {

                for (TripPlanBedrockService.PlannedItem plannedItem
                        : plannedDay.items()) {

                    TripPlanItemResponse item =
                            toPlanItemResponse(
                                    date,
                                    plannedItem,
                                    trip,
                                    attractionMap,
                                    restaurantMap,
                                    cafeMap
                            );

                    if (item != null) {
                        items.add(item);
                    }
                }
            }

            if (!lastDay) {

                items.add(
                        accommodationItemWithCategory(
                                candidatePool.accommodation(),
                                date,
                                LocalTime.of(21, 0),
                                localSegmentMode(trip),
                                "NIGHT_RETURN",
                                firstDay
                                        ? "첫날 관광과 저녁 식사를 모두 마친 뒤 선택한 숙소로 이동합니다."
                                        : "저녁 식사와 현지 일정을 마친 뒤 선택한 숙소로 복귀합니다."
                        )
                );
            }

            if (lastDay) {
                appendTripEnd(
                        trip,
                        returnFlight,
                        items
                );
            }

            result.add(
                    new TripPlanDayResponse(
                            dayNumber,
                            date,
                            applyOrders(items),
                            List.of()
                    )
            );
        }

        return result;
    }

    private void appendTripStart(
            Trip trip,
            FlightCandidate outboundFlight,
            List<TripPlanItemResponse> items
    ) {

        LocalDateTime tripStart =
                LocalDateTime.of(
                        trip.getStartDate(),
                        trip.getStartTime()
                );

        /*
         * AIR 여행은 "출발지역 -> 공항"을 별도 일정으로 만들지 않는다.
         * 선택한 항공편의 출발 공항을 여행 시작점으로 사용한다.
         */
        if (
                trip.getMainTransportMode()
                        != MainTransportMode.AIR
                        || outboundFlight == null
        ) {
            items.add(
                    new TripPlanItemResponse(
                            0,
                            TripPlanItemType.DEPARTURE,
                            null,
                            null,
                            trip.getDeparture(),
                            "TRIP_ORIGIN",
                            trip.getDepartureLatitude(),
                            trip.getDepartureLongitude(),
                            tripStart,
                            null,
                            null,
                            null,
                            "출발지역에서 여행을 시작합니다."
                    )
            );
            return;
        }

        LocalDateTime airportTargetTime =
                outboundFlight.departureTime()
                        .minusMinutes(
                                AIRPORT_BUFFER_MINUTES
                        );

        // Trip.startTime은 항공 검색 하한이다. 탑승 준비 시작을 이륙 시각으로 잘라내지 않는다.

        AirportInfo departureAirport =
                airportInfo(
                        outboundFlight.departureAirport()
                );

        AirportInfo arrivalAirport =
                airportInfo(
                        outboundFlight.arrivalAirport()
                );

        items.add(
                new TripPlanItemResponse(
                        0,
                        TripPlanItemType.AIRPORT,
                        null,
                        outboundFlight.departureAirport(),
                        departureAirport.name(),
                        "DEPARTURE_AIRPORT",
                        departureAirport.latitude(),
                        departureAirport.longitude(),
                        airportTargetTime,
                        outboundFlight.departureTime(),
                        null,
                        null,
                        "선택한 항공편으로 여행을 시작합니다."
                )
        );

        items.add(
                flightItem(
                        outboundFlight,
                        "가는 편으로 목적지 공항까지 이동합니다."
                )
        );

        items.add(
                new TripPlanItemResponse(
                        0,
                        TripPlanItemType.AIRPORT,
                        null,
                        outboundFlight.arrivalAirport(),
                        arrivalAirport.name(),
                        "ARRIVAL_AIRPORT",
                        arrivalAirport.latitude(),
                        arrivalAirport.longitude(),
                        outboundFlight.arrivalTime(),
                        outboundFlight.arrivalTime().plusMinutes(FlightTimePolicy.ARRIVAL_PROCESSING_MINUTES),
                        FlightTimePolicy.ARRIVAL_PROCESSING_MINUTES,
                        null,
                        "목적지 공항 도착 후 하차·수하물 수령에 기본 30분을 확보합니다."
                )
        );
    }

    private void appendTripEnd(
            Trip trip,
            FlightCandidate returnFlight,
            List<TripPlanItemResponse> items
    ) {

        /*
         * AIR 여행은 복귀 공항/원래 출발지역 카드를 추가하지 않고
         * 선택한 오는 편 항공편으로 여행을 종료한다.
         */
        if (
                trip.getMainTransportMode()
                        == MainTransportMode.AIR
                        && returnFlight != null
        ) {

            LocalDateTime airportArrivalTarget =
                    returnFlight.departureTime()
                            .minusMinutes(
                                    AIRPORT_BUFFER_MINUTES
                            );

            AirportInfo departureAirport =
                    airportInfo(
                            returnFlight.departureAirport()
                    );

            items.add(
                    new TripPlanItemResponse(
                            0,
                            TripPlanItemType.AIRPORT,
                            null,
                            returnFlight.departureAirport(),
                            departureAirport.name(),
                            "RETURN_DEPARTURE_AIRPORT",
                            departureAirport.latitude(),
                            departureAirport.longitude(),
                            airportArrivalTarget,
                            returnFlight.departureTime(),
                            null,
                            localSegmentMode(trip),
                            "오는 편 출발 90분 전까지 공항에 도착하여 탑승을 준비합니다."
                    )
            );

            items.add(
                    flightItem(
                            returnFlight,
                            "오는 편 항공편으로 여행을 종료합니다."
                    )
            );

            return;
        }

        LocalDateTime tripEnd =
                LocalDateTime.of(
                        trip.getEndDate(),
                        trip.getEndTime()
                );

        items.add(
                new TripPlanItemResponse(
                        0,
                        TripPlanItemType.DEPARTURE,
                        null,
                        null,
                        trip.getDeparture(),
                        "TRIP_END",
                        trip.getDepartureLatitude(),
                        trip.getDepartureLongitude(),
                        null,
                        tripEnd,
                        null,
                        null,
                        "원래 출발지역으로 돌아오며 여행을 종료합니다."
                )
        );
    }

    private TripPlanItemResponse flightItem(
            FlightCandidate flight,
            String reason
    ) {

        String name =
                (flight.airline() == null
                        ? "항공편"
                        : flight.airline())
                        + " "
                        + (flight.flightNumber() == null
                        ? ""
                        : flight.flightNumber());

        String route =
                flight.departureAirport()
                        + " → "
                        + flight.arrivalAirport();

        return new TripPlanItemResponse(
                0,
                TripPlanItemType.FLIGHT,
                null,
                flight.id(),
                name.trim(),
                route,
                null,
                null,
                flight.departureTime(),
                flight.arrivalTime(),
                null,
                SegmentTransportMode.AIR,
                reason
        );
    }

    private LocalTime plannedDayStartTime(
            TripPlanBedrockService.PlannedDay plannedDay,
            LocalTime fallback
    ) {
        if (plannedDay == null || plannedDay.items() == null) {
            return fallback;
        }

        return plannedDay.items()
                .stream()
                .map(TripPlanBedrockService.PlannedItem::startTime)
                .filter(java.util.Objects::nonNull)
                .min(LocalTime::compareTo)
                .orElse(fallback);
    }

    private LocalTime parseAccommodationTime(
            String value,
            LocalTime fallback
    ) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return LocalTime.parse(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private TripPlanItemResponse accommodationItemWithCategory(
            TripPlanSelectedAccommodation accommodation,
            LocalDate date,
            LocalTime startTime,
            SegmentTransportMode mode,
            String category,
            String reason
    ) {
        LocalDateTime startAt =
                startTime == null
                        ? null
                        : LocalDateTime.of(date, startTime);

        LocalDateTime endAt =
                "CHECK_IN".equals(category) && startAt != null
                        ? startAt.plusMinutes(30)
                        : null;

        return new TripPlanItemResponse(
                0,
                TripPlanItemType.ACCOMMODATION,
                accommodation.accommodationId(),
                accommodation.providerId(),
                accommodation.name(),
                category,
                accommodation.latitude(),
                accommodation.longitude(),
                startAt,
                endAt,
                null,
                mode,
                reason
        );
    }

    private TripPlanItemResponse accommodationItem(
            TripPlanSelectedAccommodation accommodation,
            LocalDate date,
            LocalTime startTime,
            SegmentTransportMode mode,
            String reason
    ) {

        LocalDateTime startAt =
                startTime == null
                        ? null
                        : LocalDateTime.of(
                        date,
                        startTime
                );

        return new TripPlanItemResponse(
                0,
                TripPlanItemType.ACCOMMODATION,
                accommodation.accommodationId(),
                accommodation.providerId(),
                accommodation.name(),
                "ACCOMMODATION",
                accommodation.latitude(),
                accommodation.longitude(),
                startAt,
                null,
                null,
                mode,
                reason
        );
    }

    private TripPlanItemResponse toPlanItemResponse(
            LocalDate date,
            TripPlanBedrockService.PlannedItem plannedItem,
            Trip trip,
            Map<Long, TripPlanCandidatePool.AttractionCandidate> attractionMap,
            Map<Long, TripPlanCandidatePool.RestaurantCandidate> restaurantMap,
            Map<Long, TripPlanCandidatePool.CafeCandidate> cafeMap
    ) {

        LocalDateTime startAt =
                plannedItem.startTime() == null
                        ? null
                        : LocalDateTime.of(
                        date,
                        plannedItem.startTime()
                );

        LocalDateTime endAt =
                startAt == null
                        ? null
                        : startAt.plusMinutes(
                        plannedItem.stayMinutes()
                );

        SegmentTransportMode mode =
                localSegmentMode(
                        trip
                );

        return switch (plannedItem.type()) {

            case ATTRACTION -> {

                TripPlanCandidatePool.AttractionCandidate item =
                        attractionMap.get(
                                plannedItem.id()
                        );

                if (item == null) {
                    yield null;
                }

                yield new TripPlanItemResponse(
                        0,
                        TripPlanItemType.ATTRACTION,
                        item.id(),
                        null,
                        item.name(),
                        item.category(),
                        item.latitude(),
                        item.longitude(),
                        startAt,
                        endAt,
                        plannedItem.stayMinutes(),
                        mode,
                        plannedItem.reason()
                );
            }

            case RESTAURANT -> {

                TripPlanCandidatePool.RestaurantCandidate item =
                        restaurantMap.get(
                                plannedItem.id()
                        );

                if (item == null) {
                    yield null;
                }

                yield new TripPlanItemResponse(
                        0,
                        TripPlanItemType.RESTAURANT,
                        item.id(),
                        null,
                        item.name(),
                        item.category(),
                        item.latitude(),
                        item.longitude(),
                        startAt,
                        endAt,
                        plannedItem.stayMinutes(),
                        mode,
                        plannedItem.reason()
                );
            }

            case CAFE -> {

                TripPlanCandidatePool.CafeCandidate item =
                        cafeMap.get(
                                plannedItem.id()
                        );

                if (item == null) {
                    yield null;
                }

                yield new TripPlanItemResponse(
                        0,
                        TripPlanItemType.CAFE,
                        item.id(),
                        null,
                        item.name(),
                        item.category(),
                        item.latitude(),
                        item.longitude(),
                        startAt,
                        endAt,
                        plannedItem.stayMinutes(),
                        mode,
                        plannedItem.reason()
                );
            }

            default ->
                    null;
        };
    }

    private SegmentTransportMode localSegmentMode(
            Trip trip
    ) {

        return SegmentTransportMode.valueOf(
                trip.getLocalTransportMode()
                        .name()
        );
    }

    private List<TripPlanItemResponse> applyOrders(
            List<TripPlanItemResponse> items
    ) {

        List<TripPlanItemResponse> result =
                new ArrayList<>();

        for (int i = 0;
             i < items.size();
             i++) {

            TripPlanItemResponse item =
                    items.get(i);

            result.add(
                    new TripPlanItemResponse(
                            i + 1,
                            item.type(),
                            item.placeId(),
                            item.referenceId(),
                            item.name(),
                            item.category(),
                            item.latitude(),
                            item.longitude(),
                            item.startAt(),
                            item.endAt(),
                            item.stayMinutes(),
                            item.transportModeFromPrevious(),
                            item.reason()
                    )
            );
        }

        return result;
    }

    private AirportInfo airportInfo(
            String code
    ) {

        if (code == null) {
            return new AirportInfo(
                    "공항",
                    null,
                    null
            );
        }

        return switch (code.toUpperCase()) {
            case "GMP" -> new AirportInfo(
                    "김포국제공항",
                    37.558311,
                    126.790586
            );
            case "CJU" -> new AirportInfo(
                    "제주국제공항",
                    33.510413,
                    126.491353
            );
            case "PUS" -> new AirportInfo(
                    "김해국제공항",
                    35.179554,
                    128.938198
            );
            case "TAE" -> new AirportInfo(
                    "대구국제공항",
                    35.894108,
                    128.658856
            );
            case "USN" -> new AirportInfo(
                    "울산공항",
                    35.593669,
                    129.351722
            );
            case "KWJ" -> new AirportInfo(
                    "광주공항",
                    35.126389,
                    126.808889
            );
            case "RSU" -> new AirportInfo(
                    "여수공항",
                    34.842328,
                    127.616850
            );
            case "HIN" -> new AirportInfo(
                    "사천공항",
                    35.088591,
                    128.071747
            );
            case "KPO" -> new AirportInfo(
                    "포항경주공항",
                    35.987858,
                    129.420383
            );
            case "CJJ" -> new AirportInfo(
                    "청주국제공항",
                    36.716600,
                    127.499100
            );
            case "KUV" -> new AirportInfo(
                    "군산공항",
                    35.903800,
                    126.615900
            );
            case "YNY" -> new AirportInfo(
                    "양양국제공항",
                    38.061300,
                    128.669200
            );
            case "WJU" -> new AirportInfo(
                    "원주공항",
                    37.438100,
                    127.960300
            );
            default -> new AirportInfo(
                    code + " 공항",
                    null,
                    null
            );
        };
    }

    private record AirportInfo(
            String name,
            Double latitude,
            Double longitude
    ) {
    }

}
