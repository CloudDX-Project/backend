package com.travel.trip.plan.service;

import com.travel.flight.dto.FlightCandidate;
import com.travel.global.exception.BusinessException;
import com.travel.global.exception.ErrorCode;
import com.travel.trip.entity.MainTransportMode;
import com.travel.trip.entity.SegmentTransportMode;
import com.travel.trip.entity.TransportSegment;
import com.travel.trip.entity.Trip;
import com.travel.trip.entity.TripDay;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class TripPlanService {

    private static final int AIRPORT_BUFFER_MINUTES =
            90;

    private final TripRepository tripRepository;
    private final TripPlanItemRepository tripPlanItemRepository;
    private final TransportSegmentRepository transportSegmentRepository;
    private final TripPlanCandidateService candidateService;
    private final TripPlanBedrockService bedrockService;

    public TripPlanService(
            TripRepository tripRepository,
            TripPlanItemRepository tripPlanItemRepository,
            TransportSegmentRepository transportSegmentRepository,
            TripPlanCandidateService candidateService,
            TripPlanBedrockService bedrockService
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
    }

    @Transactional
    public TripPlanResponse createPlan(
            Long userId,
            Long tripId
    ) {

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

        persistPlanItems(
                trip,
                days
        );

        rebuildMockTransportSegments(
                trip,
                days
        );

        return new TripPlanResponse(
                trip.getId(),
                plannerResult.planner(),
                "AI_ESTIMATE_ROUTING_NOT_APPLIED",
                trip.getMainTransportMode(),
                trip.getLocalTransportMode(),
                candidatePool.accommodation(),
                outboundFlight,
                returnFlight,
                candidatePool.weather(),
                candidatePool.attractions().size(),
                candidatePool.restaurants().size(),
                candidatePool.cafes().size(),
                days
        );
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
    }

    private void persistPlanItems(
            Trip trip,
            List<TripPlanDayResponse> days
    ) {
        Map<Integer, TripDay> tripDayMap =
                new HashMap<>();

        for (TripDay tripDay : trip.getTripDays()) {
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

    private void rebuildMockTransportSegments(
            Trip trip,
            List<TripPlanDayResponse> days
    ) {
        Map<Integer, TripDay> tripDayMap =
                new HashMap<>();

        for (TripDay tripDay : trip.getTripDays()) {
            tripDay.clearTransportSegments();
            tripDayMap.put(
                    tripDay.getDayNumber(),
                    tripDay
            );
        }

        tripRepository.flush();

        List<TransportSegment> newSegments =
                new ArrayList<>();

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

                SegmentTransportMode mode =
                        current.transportModeFromPrevious();

                if (mode == null || mode == SegmentTransportMode.AIR) {
                    continue;
                }

                if (!hasCoordinates(previous) || !hasCoordinates(current)) {
                    continue;
                }

                double straightDistanceKm =
                        haversineKm(
                                previous.latitude(),
                                previous.longitude(),
                                current.latitude(),
                                current.longitude()
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

                long cost =
                        mockTransportCost(
                                mode,
                                distanceKm
                        );

                LocalDateTime departureAt =
                        previous.endAt() != null
                                ? previous.endAt()
                                : previous.startAt();

                LocalDateTime arrivalAt =
                        departureAt == null
                                ? current.startAt()
                                : departureAt.plusMinutes(
                                durationMinutes
                        );

                TransportSegment segment =
                        TransportSegment.builder()
                                .tripDay(tripDay)
                                .sequence(sequence++)
                                .mode(mode)
                                .departureName(previous.name())
                                .arrivalName(current.name())
                                .departureLatitude(previous.latitude())
                                .departureLongitude(previous.longitude())
                                .arrivalLatitude(current.latitude())
                                .arrivalLongitude(current.longitude())
                                .departureAt(departureAt)
                                .arrivalAt(arrivalAt)
                                .distanceKm(distanceKm)
                                .durationMinutes(durationMinutes)
                                .cost(cost)
                                .build();

                tripDay.addTransportSegment(
                        segment
                );

                newSegments.add(
                        segment
                );
            }
        }

        transportSegmentRepository.saveAll(
                newSegments
        );

        transportSegmentRepository.flush();
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
            case RENTAL_CAR, OWN_CAR, TAXI, PUBLIC_TRANSIT -> 1.25;
            case AIR, KTX, SRT, EXPRESS_BUS -> 1.0;
        };
    }

    private double averageSpeedKmh(
            SegmentTransportMode mode
    ) {
        return switch (mode) {
            case WALK -> 4.5;
            case PUBLIC_TRANSIT -> 30.0;
            case RENTAL_CAR, OWN_CAR, TAXI -> 45.0;
            case KTX, SRT -> 180.0;
            case EXPRESS_BUS -> 70.0;
            case AIR -> 500.0;
        };
    }

    private long mockTransportCost(
            SegmentTransportMode mode,
            double distanceKm
    ) {
        if (
                mode != SegmentTransportMode.RENTAL_CAR
                        && mode != SegmentTransportMode.OWN_CAR
        ) {
            return 0L;
        }

        /*
         * MVP 목업 유류비
         * - 평균 연비: 10 km/L
         * - 유가: 1,700원/L
         * 실제 렌터카/유가 API 연동 시 교체한다.
         */
        double liters =
                distanceKm / 10.0;

        return Math.round(
                liters * 1_700.0
        );
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
                items.add(
                        accommodationItem(
                                candidatePool.accommodation(),
                                date,
                                null,
                                null,
                                "숙소에서 하루 일정을 시작합니다."
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
                        accommodationItem(
                                candidatePool.accommodation(),
                                date,
                                null,
                                localSegmentMode(trip),
                                "현지 일정을 마치고 선택한 숙소로 복귀합니다."
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
                            applyOrders(items)
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

        if (
                trip.getMainTransportMode()
                        != MainTransportMode.AIR
                        || outboundFlight == null
        ) {
            return;
        }

        LocalDateTime airportTargetTime =
                outboundFlight.departureTime()
                        .minusMinutes(
                                AIRPORT_BUFFER_MINUTES
                        );

        if (airportTargetTime.isBefore(tripStart)) {
            airportTargetTime = tripStart;
        }

        items.add(
                new TripPlanItemResponse(
                        0,
                        TripPlanItemType.AIRPORT,
                        null,
                        outboundFlight.departureAirport(),
                        airportName(
                                outboundFlight.departureAirport()
                        ),
                        "DEPARTURE_AIRPORT",
                        null,
                        null,
                        airportTargetTime,
                        outboundFlight.departureTime(),
                        null,
                        null,
                        "선택한 항공편 탑승을 위해 출발 공항으로 이동합니다."
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
                        airportName(
                                outboundFlight.arrivalAirport()
                        ),
                        "ARRIVAL_AIRPORT",
                        null,
                        null,
                        outboundFlight.arrivalTime(),
                        null,
                        null,
                        null,
                        "목적지 공항에 도착했습니다."
                )
        );
    }

    private void appendTripEnd(
            Trip trip,
            FlightCandidate returnFlight,
            List<TripPlanItemResponse> items
    ) {

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

            items.add(
                    new TripPlanItemResponse(
                            0,
                            TripPlanItemType.AIRPORT,
                            null,
                            returnFlight.departureAirport(),
                            airportName(
                                    returnFlight.departureAirport()
                            ),
                            "RETURN_DEPARTURE_AIRPORT",
                            null,
                            null,
                            airportArrivalTarget,
                            returnFlight.departureTime(),
                            null,
                            localSegmentMode(trip),
                            "오는 편 탑승을 위해 목적지 공항으로 이동합니다."
                    )
            );

            items.add(
                    flightItem(
                            returnFlight,
                            "오는 편으로 출발지역 공항까지 이동합니다."
                    )
            );

            items.add(
                    new TripPlanItemResponse(
                            0,
                            TripPlanItemType.AIRPORT,
                            null,
                            returnFlight.arrivalAirport(),
                            airportName(
                                    returnFlight.arrivalAirport()
                            ),
                            "RETURN_ARRIVAL_AIRPORT",
                            null,
                            null,
                            returnFlight.arrivalTime(),
                            null,
                            null,
                            null,
                            "출발지역 공항에 도착했습니다."
                    )
            );
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
                        returnFlight == null
                                ? null
                                : returnFlight.arrivalTime(),
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

    private String airportName(
            String code
    ) {

        if (code == null) {
            return "공항";
        }

        return switch (code.toUpperCase()) {
            case "GMP" -> "김포국제공항";
            case "CJU" -> "제주국제공항";
            case "PUS" -> "김해국제공항";
            case "TAE" -> "대구국제공항";
            case "USN" -> "울산공항";
            case "KWJ" -> "광주공항";
            case "RSU" -> "여수공항";
            case "HIN" -> "사천공항";
            case "KPO" -> "포항경주공항";
            case "CJJ" -> "청주국제공항";
            case "KUV" -> "군산공항";
            case "YNY" -> "양양국제공항";
            case "WJU" -> "원주공항";
            default -> code + " 공항";
        };
    }
}