package com.travel.trip.plan.service;

import com.travel.routing.dto.DrivingRouteResult;
import com.travel.routing.service.RoutingService;
import com.travel.global.time.ScheduleTime;
import com.travel.trip.entity.LocalTransportMode;
import com.travel.trip.entity.SegmentTransportMode;
import com.travel.trip.entity.Trip;
import com.travel.trip.entity.TripRentalSelection;
import com.travel.trip.plan.dto.TripPlanCandidatePool;
import com.travel.trip.plan.dto.TripPlanDayResponse;
import com.travel.trip.plan.dto.TripPlanItemResponse;
import com.travel.trip.plan.type.TripPlanItemType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Bedrock의 일정은 후보 선택안일 뿐이다. 이 클래스는 최종 Kakao 이동시간을
 * 기준으로 서버가 반드시 보장해야 하는 식사 슬롯과 항공 안전시간을 보정한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripPlanSchedulePostProcessor {

    private static final LocalTime BREAKFAST_AT = LocalTime.of(8, 0);
    private static final LocalTime LUNCH_AT = LocalTime.of(12, 30);
    private static final LocalTime DINNER_AT = LocalTime.of(18, 30);
    private static final LocalTime FIRST_DAY_DINNER_EARLIEST = LocalTime.of(17, 30);
    private static final LocalTime DINNER_FALLBACK_EARLIEST = LocalTime.of(17, 0);
    private static final int LONG_IDLE_GAP_MINUTES = 75;
    private static final int MAX_GAP_CANDIDATE_CHECKS = 6;
    private static final int MAX_GAP_INSERTIONS = 2;
    private static final double MIN_BREAKFAST_FIT_SCORE = 0.50;

    private final RoutingService routingService;

    /**
     * 첫날/마지막날이 아닌 날에는 RESTAURANT를 정확히 세 개만 남기고
     * BREAKFAST/LUNCH/DINNER 후보를 각각 하나씩 서버가 다시 선택한다.
     */
    public List<TripPlanDayResponse> repairMealSlots(
            Trip trip,
            TripPlanCandidatePool candidatePool,
            List<TripPlanDayResponse> days
    ) {
        if (days.size() < 3 || candidatePool.restaurants().isEmpty()) {
            return days;
        }

        Map<Long, TripPlanCandidatePool.RestaurantCandidate> restaurantMap =
                new HashMap<>();
        candidatePool.restaurants().forEach(item -> restaurantMap.put(item.id(), item));

        // 첫날/마지막날에서 이미 사용한 식당은 중간 날 교체 후보에서 제외한다.
        Set<Long> usedRestaurantIds = new HashSet<>();
        for (TripPlanDayResponse day : days) {
            if (day.dayNumber() == 1 || day.dayNumber() == days.size()) {
                day.items().stream()
                        .filter(item -> item.type() == TripPlanItemType.RESTAURANT)
                        .map(TripPlanItemResponse::placeId)
                        .filter(java.util.Objects::nonNull)
                        .forEach(usedRestaurantIds::add);
            }
        }

        List<TripPlanDayResponse> result = new ArrayList<>();
        for (TripPlanDayResponse day : days) {
            boolean middleDay = day.dayNumber() > 1 && day.dayNumber() < days.size();
            if (!middleDay) {
                result.add(day);
                continue;
            }

            List<Long> currentRestaurantIds = day.items().stream()
                    .filter(item -> item.type() == TripPlanItemType.RESTAURANT)
                    .map(TripPlanItemResponse::placeId)
                    .filter(restaurantMap::containsKey)
                    .toList();

            EnumMap<MealSlot, TripPlanCandidatePool.RestaurantCandidate> selected =
                    new EnumMap<>(MealSlot.class);

            for (MealSlot slot : MealSlot.values()) {
                TripPlanCandidatePool.RestaurantCandidate candidate = chooseRestaurant(
                        candidatePool.restaurants(),
                        currentRestaurantIds,
                        usedRestaurantIds,
                        slot
                );
                if (candidate != null) {
                    selected.put(slot, candidate);
                    usedRestaurantIds.add(candidate.id());
                }
            }

            if (selected.size() < MealSlot.values().length) {
                log.warn(
                        "중간 날 3식 보정에 필요한 서로 다른 식당 후보가 부족합니다. day={}, selected={}",
                        day.dayNumber(),
                        selected.size()
                );
            }

            List<TripPlanItemResponse> repaired = buildMiddleDaySchedule(
                    trip,
                    day,
                    selected
            );
            result.add(copyDay(day, applyOrders(repaired)));
        }

        return result;
    }

    /**
     * 첫날에는 체크인 가능 시각에 바로 숙소로 보내지 않고 저녁 식사를 보장한다.
     * 숙소 카드는 실제 하루 일정이 끝난 뒤의 NIGHT_RETURN 한 건으로만 유지한다.
     */
    public List<TripPlanDayResponse> ensureFirstDayDinner(
            Trip trip,
            TripPlanCandidatePool candidatePool,
            List<TripPlanDayResponse> days
    ) {
        if (days.isEmpty() || candidatePool.restaurants().isEmpty()) {
            return days;
        }

        TripPlanDayResponse firstDay = days.get(0);
        boolean alreadyHasDinner = firstDay.items().stream()
                .filter(item -> item.type() == TripPlanItemType.RESTAURANT)
                .map(TripPlanItemResponse::startAt)
                .filter(java.util.Objects::nonNull)
                .anyMatch(time -> !time.toLocalTime().isBefore(FIRST_DAY_DINNER_EARLIEST));
        if (alreadyHasDinner) {
            return days;
        }

        List<TripPlanItemResponse> items = new ArrayList<>(firstDay.items());
        int nightReturnIndex = findCategoryIndex(items, "NIGHT_RETURN");
        if (nightReturnIndex <= 0) {
            return days;
        }

        TripPlanItemResponse previous = items.get(nightReturnIndex - 1);
        LocalDateTime previousEnd = endOrStart(previous);
        if (previousEnd == null) {
            return days;
        }

        Set<Long> usedRestaurantIds = new HashSet<>();
        days.forEach(day -> day.items().stream()
                .filter(item -> item.type() == TripPlanItemType.RESTAURANT)
                .map(TripPlanItemResponse::placeId)
                .filter(java.util.Objects::nonNull)
                .forEach(usedRestaurantIds::add));

        TripPlanCandidatePool.RestaurantCandidate candidate = chooseRestaurant(
                candidatePool.restaurants(), List.of(), usedRestaurantIds, MealSlot.DINNER
        );
        if (candidate == null) {
            return days;
        }

        OptionalLong travelMinutes = routeMinutes(
                previous.latitude(), previous.longitude(),
                candidate.latitude(), candidate.longitude()
        );
        if (travelMinutes.isEmpty()) {
            return days;
        }

        LocalDateTime earliestArrival = previousEnd.plusMinutes(travelMinutes.getAsLong());
        LocalDateTime dinnerTarget = firstDay.date().atTime(FIRST_DAY_DINNER_EARLIEST);
        LocalDateTime dinnerStart = earliestArrival.isAfter(dinnerTarget)
                ? earliestArrival
                : dinnerTarget;
        TripPlanItemResponse dinner = restaurantItem(
                dinnerStart,
                candidate,
                localSegmentMode(trip),
                MealSlot.DINNER
        );

        items.add(nightReturnIndex, dinner);
        List<TripPlanDayResponse> result = new ArrayList<>(days);
        result.set(0, copyDay(firstDay, applyOrders(items)));
        return result;
    }

    /**
     * 이미 선택된 일정만 재배열하지 않고 전체 후보 풀에서 미사용 관광지/카페를 찾아
     * 90분 이상의 긴 공백을 채운다. 외부 경로 API 호출을 제한하기 위해 점수 상위
     * 후보 일부만 실제 경로로 검증한다.
     */
    public List<TripPlanDayResponse> fillLongIdleGaps(
            Trip trip,
            TripPlanCandidatePool candidatePool,
            List<TripPlanDayResponse> days
    ) {
        if (days.isEmpty()) {
            return days;
        }

        Set<String> usedKeys = collectUsedPlaceKeys(days);
        List<TripPlanDayResponse> result = new ArrayList<>();

        for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
            TripPlanDayResponse day = days.get(dayIndex);
            if (dayIndex == days.size() - 1 || day.items().size() < 2) {
                result.add(day);
                continue;
            }

            List<TripPlanItemResponse> source = new ArrayList<>(day.items());
            List<TripPlanItemResponse> repaired = new ArrayList<>();
            for (int i = 0; i < source.size() - 1; i++) {
                TripPlanItemResponse current = source.get(i);
                TripPlanItemResponse next = source.get(i + 1);
                repaired.add(current);

                LocalDateTime currentEnd = endOrStart(current);
                LocalDateTime nextStart = next.startAt();
                if (currentEnd == null || nextStart == null
                        || ScheduleTime.minutesBetween(currentEnd, nextStart)
                        < LONG_IDLE_GAP_MINUTES) {
                    continue;
                }

                TripPlanItemResponse gapCursor = current;
                LocalDateTime gapCursorEnd = currentEnd;
                int inserted = 0;

                while (inserted < MAX_GAP_INSERTIONS
                        && ScheduleTime.minutesBetween(gapCursorEnd, nextStart) >= LONG_IDLE_GAP_MINUTES) {
                    CandidateVisit visit = findGapVisit(
                            trip, candidatePool, gapCursor, gapCursorEnd, next, nextStart, usedKeys
                    );
                    if (visit == null) {
                        break;
                    }
                    repaired.add(visit.item());
                    usedKeys.add(placeKey(visit.item().type(), visit.item().placeId()));
                    gapCursor = visit.item();
                    gapCursorEnd = endOrStart(visit.item());
                    inserted++;
                }

                if (inserted == 0) {
                    TripPlanItemResponse shiftedDinner = shiftDinnerEarlier(
                            current, currentEnd, next, day.date()
                    );
                    if (shiftedDinner != next) {
                        source.set(i + 1, shiftedDinner);
                    }
                }
            }
            repaired.add(source.get(source.size() - 1));
            result.add(copyDay(day, applyOrders(repaired)));
        }

        return result;
    }

    /**
     * 21시는 후보 삽입을 위한 숙소 도착 상한일 뿐 실제 고정 도착 시각이 아니다.
     * 후보 삽입이 끝나면 anchor를 해제해 마지막 활동에서 숙소까지의 실제 이동시간으로
     * 최종 도착 시각을 다시 계산하게 한다.
     */
    public List<TripPlanDayResponse> releaseNightReturnDeadlines(
            List<TripPlanDayResponse> days
    ) {
        List<TripPlanDayResponse> result = new ArrayList<>();
        for (TripPlanDayResponse day : days) {
            List<TripPlanItemResponse> items = day.items().stream()
                    .map(item -> "NIGHT_RETURN".equals(item.category())
                            ? copyWithTimesAndReason(
                                    item, null, null, item.stayMinutes(), item.reason()
                            )
                            : item)
                    .toList();
            result.add(copyDay(day, applyOrders(items)));
        }
        return result;
    }

    private CandidateVisit findGapVisit(
            Trip trip,
            TripPlanCandidatePool pool,
            TripPlanItemResponse current,
            LocalDateTime currentEnd,
            TripPlanItemResponse next,
            LocalDateTime nextStart,
            Set<String> usedKeys
    ) {
        CandidateVisit visit = findAttractionGapVisit(
                trip, pool, current, currentEnd, next, nextStart, usedKeys, 90
        );
        if (visit != null) {
            return visit;
        }
        visit = findCafeGapVisit(
                trip, pool, current, currentEnd, next, nextStart, usedKeys, 60
        );
        if (visit != null) {
            return visit;
        }
        visit = findAttractionGapVisit(
                trip, pool, current, currentEnd, next, nextStart, usedKeys, 45
        );
        if (visit != null) {
            return visit;
        }
        return findCafeGapVisit(
                trip, pool, current, currentEnd, next, nextStart, usedKeys, 45
        );
    }

    private CandidateVisit findAttractionGapVisit(
            Trip trip,
            TripPlanCandidatePool pool,
            TripPlanItemResponse current,
            LocalDateTime currentEnd,
            TripPlanItemResponse next,
            LocalDateTime nextStart,
            Set<String> usedKeys,
            int stayMinutes
    ) {
        return pool.attractions().stream()
                .filter(item -> !usedKeys.contains(placeKey(TripPlanItemType.ATTRACTION, item.id())))
                .sorted(Comparator.comparingDouble(
                        (TripPlanCandidatePool.AttractionCandidate item) -> safe(item.recommendationScore())
                ).reversed())
                .limit(MAX_GAP_CANDIDATE_CHECKS)
                .map(item -> feasibleGapVisit(
                        trip, current, currentEnd, next, nextStart,
                        TripPlanItemType.ATTRACTION, item.id(), item.name(), item.category(),
                        item.latitude(), item.longitude(), stayMinutes,
                        stayMinutes >= 90
                                ? "긴 대기 시간을 줄이면서 다음 일정까지 이동 가능한 관광 일정입니다."
                                : "남는 시간과 실제 이동시간에 맞춘 짧은 관광 일정입니다."
                ))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private CandidateVisit findCafeGapVisit(
            Trip trip,
            TripPlanCandidatePool pool,
            TripPlanItemResponse current,
            LocalDateTime currentEnd,
            TripPlanItemResponse next,
            LocalDateTime nextStart,
            Set<String> usedKeys,
            int stayMinutes
    ) {
        return pool.cafes().stream()
                .filter(item -> !usedKeys.contains(placeKey(TripPlanItemType.CAFE, item.id())))
                .sorted(Comparator.comparingDouble(
                        (TripPlanCandidatePool.CafeCandidate item) ->
                                safe(item.qualityScore()) + safe(item.recommendationScore()) * 0.25
                ).reversed())
                .limit(MAX_GAP_CANDIDATE_CHECKS)
                .map(item -> feasibleGapVisit(
                        trip, current, currentEnd, next, nextStart,
                        TripPlanItemType.CAFE, item.id(), item.name(), item.category(),
                        item.latitude(), item.longitude(), stayMinutes,
                        stayMinutes >= 60
                                ? "긴 대기 시간을 줄이면서 다음 일정까지 이동 가능한 카페 일정입니다."
                                : "남는 시간과 실제 이동시간에 맞춘 짧은 카페 일정입니다."
                ))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /** 후보 일정이 들어가지 못하면 18:30 고정을 버리고 저녁을 17시 이후로 앞당긴다. */
    private TripPlanItemResponse shiftDinnerEarlier(
            TripPlanItemResponse current,
            LocalDateTime currentEnd,
            TripPlanItemResponse next,
            java.time.LocalDate date
    ) {
        if (next.type() != TripPlanItemType.RESTAURANT
                || next.startAt() == null
                || next.startAt().toLocalTime().isBefore(DINNER_FALLBACK_EARLIEST)) {
            return next;
        }

        OptionalLong travelMinutes = routeMinutes(
                current.latitude(), current.longitude(),
                next.latitude(), next.longitude()
        );
        if (travelMinutes.isEmpty()) {
            return next;
        }

        LocalDateTime earliestArrival = currentEnd.plusMinutes(travelMinutes.getAsLong());
        LocalDateTime fallbackStart = date.atTime(DINNER_FALLBACK_EARLIEST);
        LocalDateTime shiftedStart = earliestArrival.isAfter(fallbackStart)
                ? earliestArrival
                : fallbackStart;
        if (!shiftedStart.isBefore(next.startAt())) {
            return next;
        }

        int stayMinutes = next.stayMinutes() == null ? 75 : next.stayMinutes();
        return copyWithTimesAndReason(
                next,
                shiftedStart,
                shiftedStart.plusMinutes(stayMinutes),
                stayMinutes,
                "긴 대기 시간을 줄이기 위해 실제 이동시간 기준으로 앞당긴 저녁 식사입니다."
        );
    }

    private CandidateVisit feasibleGapVisit(
            Trip trip,
            TripPlanItemResponse current,
            LocalDateTime currentEnd,
            TripPlanItemResponse next,
            LocalDateTime nextStart,
            TripPlanItemType type,
            Long id,
            String name,
            String category,
            Double latitude,
            Double longitude,
            int stayMinutes,
            String reason
    ) {
        OptionalLong toCandidate = routeMinutes(
                current.latitude(), current.longitude(), latitude, longitude
        );
        OptionalLong toNext = routeMinutes(
                latitude, longitude, next.latitude(), next.longitude()
        );
        if (toCandidate.isEmpty() || toNext.isEmpty()) {
            return null;
        }

        LocalDateTime startAt = currentEnd.plusMinutes(toCandidate.getAsLong());
        LocalDateTime endAt = startAt.plusMinutes(stayMinutes);
        if (endAt.plusMinutes(toNext.getAsLong()).isAfter(nextStart)) {
            return null;
        }

        return new CandidateVisit(new TripPlanItemResponse(
                0, type, id, null, name, category, latitude, longitude,
                startAt, endAt, stayMinutes, localSegmentMode(trip), reason
        ));
    }

    private List<TripPlanItemResponse> buildMiddleDaySchedule(
            Trip trip,
            TripPlanDayResponse day,
            EnumMap<MealSlot, TripPlanCandidatePool.RestaurantCandidate> selected
    ) {
        TripPlanItemResponse dayStart = day.items().stream()
                .filter(item -> "DAY_START".equals(item.category()))
                .findFirst()
                .orElse(null);
        TripPlanItemResponse nightReturn = day.items().stream()
                .filter(item -> "NIGHT_RETURN".equals(item.category()))
                .findFirst()
                .orElse(null);

        List<TripPlanItemResponse> flexible = day.items().stream()
                .filter(item -> item.type() == TripPlanItemType.ATTRACTION
                        || item.type() == TripPlanItemType.CAFE)
                .sorted(itemTimeComparator())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        List<TripPlanItemResponse> result = new ArrayList<>();
        if (dayStart != null) {
            result.add(dayStart);
        }

        TripPlanItemResponse breakfast = mealItem(day, selected, MealSlot.BREAKFAST, trip);
        TripPlanItemResponse lunch = mealItem(day, selected, MealSlot.LUNCH, trip);
        TripPlanItemResponse dinner = mealItem(day, selected, MealSlot.DINNER, trip);

        if (breakfast != null) {
            breakfast = alignAnchorAfterPrevious(result, breakfast);
            result.add(breakfast);
        }

        addFlexibleItemsThatFit(
                result,
                flexible,
                lunch,
                day.date().atTime(LocalTime.of(9, 0)),
                day.date().atTime(LocalTime.of(11, 30))
        );
        if (lunch != null) {
            lunch = alignAnchorAfterPrevious(result, lunch);
            result.add(lunch);
        }

        addFlexibleItemsThatFit(
                result,
                flexible,
                dinner,
                day.date().atTime(LocalTime.of(14, 0)),
                day.date().atTime(LocalTime.of(17, 30))
        );
        if (dinner != null) {
            dinner = alignAnchorAfterPrevious(result, dinner);
            result.add(dinner);
        }

        if (nightReturn != null) {
            result.add(nightReturn);
        }
        return result;
    }

    private TripPlanItemResponse mealItem(
            TripPlanDayResponse day,
            EnumMap<MealSlot, TripPlanCandidatePool.RestaurantCandidate> selected,
            MealSlot slot,
            Trip trip
    ) {
        TripPlanCandidatePool.RestaurantCandidate candidate = selected.get(slot);
        if (candidate == null) {
            return null;
        }
        return restaurantItem(
                day.date().atTime(slot.targetTime),
                candidate,
                localSegmentMode(trip),
                slot
        );
    }

    /**
     * 다음 식사 anchor를 늦추지 않는 관광/카페만 유지한다. 따라서 실제 라우팅 후에도
     * 점심과 저녁이 같은 슬롯으로 밀려나는 중복 식사 문제가 생기지 않는다.
     */
    private void addFlexibleItemsThatFit(
            List<TripPlanItemResponse> scheduled,
            List<TripPlanItemResponse> remaining,
            TripPlanItemResponse nextMeal,
            LocalDateTime originalStartInclusive,
            LocalDateTime originalStartExclusive
    ) {
        if (scheduled.isEmpty() || nextMeal == null) {
            return;
        }

        List<TripPlanItemResponse> candidates = remaining.stream()
                .filter(item -> item.startAt() == null
                        || (!item.startAt().isBefore(originalStartInclusive)
                        && item.startAt().isBefore(originalStartExclusive)))
                .toList();

        for (TripPlanItemResponse candidate : candidates) {
            TripPlanItemResponse previous = scheduled.get(scheduled.size() - 1);
            LocalDateTime previousEnd = endOrStart(previous);
            if (previousEnd == null) {
                continue;
            }

            OptionalLong toCandidate = routeMinutes(
                    previous.latitude(), previous.longitude(),
                    candidate.latitude(), candidate.longitude()
            );
            OptionalLong toMeal = routeMinutes(
                    candidate.latitude(), candidate.longitude(),
                    nextMeal.latitude(), nextMeal.longitude()
            );
            if (toCandidate.isEmpty() || toMeal.isEmpty()) {
                continue;
            }

            int stayMinutes = candidate.stayMinutes() == null
                    ? (candidate.type() == TripPlanItemType.CAFE ? 60 : 90)
                    : candidate.stayMinutes();
            LocalDateTime candidateStart = previousEnd.plusMinutes(toCandidate.getAsLong());
            LocalDateTime candidateEnd = candidateStart.plusMinutes(stayMinutes);
            LocalDateTime arrivalAtMeal = candidateEnd.plusMinutes(toMeal.getAsLong());

            if (arrivalAtMeal.isAfter(nextMeal.startAt())) {
                continue;
            }

            scheduled.add(copyWithTimesAndReason(
                    candidate,
                    candidateStart,
                    candidateEnd,
                    stayMinutes,
                    candidate.reason()
            ));
            remaining.remove(candidate);
        }
    }

    private TripPlanItemResponse alignAnchorAfterPrevious(
            List<TripPlanItemResponse> scheduled,
            TripPlanItemResponse anchor
    ) {
        if (scheduled.isEmpty()) {
            return anchor;
        }

        TripPlanItemResponse previous = scheduled.get(scheduled.size() - 1);
        LocalDateTime previousEnd = endOrStart(previous);
        OptionalLong travelMinutes = routeMinutes(
                previous.latitude(), previous.longitude(),
                anchor.latitude(), anchor.longitude()
        );
        if (previousEnd == null || travelMinutes.isEmpty()) {
            return anchor;
        }

        LocalDateTime earliest = previousEnd.plusMinutes(travelMinutes.getAsLong());
        if (!earliest.isAfter(anchor.startAt())) {
            return anchor;
        }

        int stayMinutes = anchor.stayMinutes() == null ? 75 : anchor.stayMinutes();
        return copyWithTimesAndReason(
                anchor,
                earliest,
                earliest.plusMinutes(stayMinutes),
                stayMinutes,
                anchor.reason()
        );
    }

    /**
     * 마지막 날 남은 분 수의 구간값을 사용하지 않는다. 후보별 Kakao 이동시간,
     * 체류시간, 렌터카 반납 지점 이동 및 셔틀까지 모두 더해 공항 목표시간 안에
     * 들어오는 경우만 한 장소를 추가한다.
     */
    public List<TripPlanDayResponse> fillLastDayByActualSlack(
            Trip trip,
            TripPlanCandidatePool candidatePool,
            List<TripPlanDayResponse> days
    ) {
        if (days.isEmpty()) {
            return days;
        }

        int lastDayIndex = days.size() - 1;
        TripPlanDayResponse lastDay = days.get(lastDayIndex);
        List<TripPlanItemResponse> items = new ArrayList<>(lastDay.items());
        int airportIndex = findCategoryIndex(items, "RETURN_DEPARTURE_AIRPORT");
        if (airportIndex <= 0) {
            return days;
        }

        TripPlanItemResponse airport = items.get(airportIndex);
        if (airport.startAt() == null) {
            return days;
        }

        TripPlanItemResponse current = items.get(airportIndex - 1);
        LocalDateTime currentEnd = endOrStart(current);
        if (currentEnd == null) {
            return days;
        }

        Set<String> usedKeys = collectUsedPlaceKeys(days);
        CandidateVisit visit = findFeasibleAttraction(
                trip, candidatePool, current, currentEnd, airport, usedKeys, 90
        );

        if (visit == null) {
            visit = findFeasibleCafe(
                    trip, candidatePool, current, currentEnd, airport, usedKeys, 45
            );
        }

        if (visit == null) {
            visit = findFeasibleAttraction(
                    trip, candidatePool, current, currentEnd, airport, usedKeys, 45
            );
        }

        if (visit == null) {
            return days;
        }

        items.add(airportIndex, visit.item());
        List<TripPlanDayResponse> result = new ArrayList<>(days);
        result.set(lastDayIndex, copyDay(lastDay, applyOrders(items)));
        return result;
    }

    /** 최종 라우팅이 끝난 startAt으로만 식사 역할과 reason을 다시 판정한다. */
    public List<TripPlanDayResponse> normalizeMealRoleAfterRouting(
            List<TripPlanDayResponse> days
    ) {
        List<TripPlanDayResponse> result = new ArrayList<>();

        for (TripPlanDayResponse day : days) {
            List<TripPlanItemResponse> normalized = new ArrayList<>();
            for (TripPlanItemResponse item : day.items()) {
                if (item.type() != TripPlanItemType.RESTAURANT || item.startAt() == null) {
                    normalized.add(item);
                    continue;
                }

                MealRole role = MealRole.from(item.startAt().toLocalTime());
                String reason = switch (role) {
                    case BREAKFAST -> "최종 일정 시간 기준 아침 식사 시간대에 맞춘 일정입니다.";
                    case LUNCH -> "최종 일정 시간 기준 점심 식사 시간대에 맞춘 일정입니다.";
                    case DINNER -> "최종 일정 시간 기준 저녁 식사 시간대에 맞춘 일정입니다.";
                    case OUTSIDE_SLOT -> "최종 일정 시간 기준 식사 가능 시간과 동선을 반영했습니다.";
                };

                normalized.add(copyWithTimesAndReason(
                        item,
                        item.startAt(),
                        item.endAt(),
                        item.stayMinutes(),
                        reason
                ));
            }
            result.add(copyDay(day, applyOrders(normalized)));
        }

        return result;
    }

    private TripPlanCandidatePool.RestaurantCandidate chooseRestaurant(
            List<TripPlanCandidatePool.RestaurantCandidate> candidates,
            List<Long> currentRestaurantIds,
            Set<Long> usedIds,
            MealSlot slot
    ) {
        Comparator<TripPlanCandidatePool.RestaurantCandidate> comparator =
                Comparator.comparingDouble(item -> restaurantScore(item, slot));

        List<TripPlanCandidatePool.RestaurantCandidate> available = candidates.stream()
                .filter(item -> !usedIds.contains(item.id()))
                .toList();

        if (slot == MealSlot.BREAKFAST) {
            List<TripPlanCandidatePool.RestaurantCandidate> breakfastSuitable = available.stream()
                    .filter(item -> safe(item.breakfastFitScore()) >= MIN_BREAKFAST_FIT_SCORE)
                    .toList();
            if (!breakfastSuitable.isEmpty()) {
                available = breakfastSuitable;
            } else {
                List<TripPlanCandidatePool.RestaurantCandidate> notDinnerHeavy = available.stream()
                        .filter(item -> safe(item.breakfastFitScore()) > 0.10)
                        .toList();
                if (!notDinnerHeavy.isEmpty()) {
                    available = notDinnerHeavy;
                }
            }
        }

        // 점수가 같으면 Bedrock이 이미 고른 후보를 유지해 결과 변동을 줄인다.
        return available.stream()
                .max(comparator.thenComparingInt(item -> currentRestaurantIds.contains(item.id()) ? 1 : 0))
                .orElse(null);
    }

    private double restaurantScore(
            TripPlanCandidatePool.RestaurantCandidate item,
            MealSlot slot
    ) {
        double fit = switch (slot) {
            case BREAKFAST -> safe(item.breakfastFitScore());
            case LUNCH -> safe(item.lunchFitScore());
            case DINNER -> safe(item.dinnerFitScore());
        };
        return safe(item.qualityScore()) * 0.65 + fit * 0.35;
    }

    private TripPlanItemResponse restaurantItem(
            LocalDateTime startAt,
            TripPlanCandidatePool.RestaurantCandidate candidate,
            SegmentTransportMode mode,
            MealSlot slot
    ) {
        int stayMinutes = slot == MealSlot.BREAKFAST ? 60 : 75;
        return new TripPlanItemResponse(
                0,
                TripPlanItemType.RESTAURANT,
                candidate.id(),
                null,
                candidate.name(),
                candidate.category(),
                candidate.latitude(),
                candidate.longitude(),
                startAt,
                startAt.plusMinutes(stayMinutes),
                stayMinutes,
                mode,
                slot.reason
        );
    }

    private CandidateVisit findFeasibleAttraction(
            Trip trip,
            TripPlanCandidatePool pool,
            TripPlanItemResponse current,
            LocalDateTime currentEnd,
            TripPlanItemResponse airport,
            Set<String> usedKeys,
            int stayMinutes
    ) {
        return pool.attractions().stream()
                .filter(item -> !usedKeys.contains(placeKey(TripPlanItemType.ATTRACTION, item.id())))
                .sorted(Comparator.comparingDouble(
                        (TripPlanCandidatePool.AttractionCandidate item) -> safe(item.recommendationScore())
                ).reversed())
                .map(item -> feasibleVisit(
                        trip,
                        current,
                        currentEnd,
                        airport,
                        TripPlanItemType.ATTRACTION,
                        item.id(),
                        item.name(),
                        item.category(),
                        item.latitude(),
                        item.longitude(),
                        stayMinutes,
                        stayMinutes >= 90
                                ? "마지막 날 실제 이동시간과 공항 도착 목표를 만족하는 관광 일정입니다."
                                : "마지막 날 실제 이동시간 안에 가능한 짧은 관광·산책 일정입니다."
                ))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private CandidateVisit findFeasibleCafe(
            Trip trip,
            TripPlanCandidatePool pool,
            TripPlanItemResponse current,
            LocalDateTime currentEnd,
            TripPlanItemResponse airport,
            Set<String> usedKeys,
            int stayMinutes
    ) {
        return pool.cafes().stream()
                .filter(item -> !usedKeys.contains(placeKey(TripPlanItemType.CAFE, item.id())))
                .sorted(Comparator.comparingDouble(
                        (TripPlanCandidatePool.CafeCandidate item) -> safe(item.qualityScore())
                                + safe(item.recommendationScore()) * 0.25
                ).reversed())
                .map(item -> feasibleVisit(
                        trip,
                        current,
                        currentEnd,
                        airport,
                        TripPlanItemType.CAFE,
                        item.id(),
                        item.name(),
                        item.category(),
                        item.latitude(),
                        item.longitude(),
                        stayMinutes,
                        "마지막 날 실제 이동시간과 공항 도착 목표 안에서 가능한 짧은 카페 일정입니다."
                ))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private CandidateVisit feasibleVisit(
            Trip trip,
            TripPlanItemResponse current,
            LocalDateTime currentEnd,
            TripPlanItemResponse airport,
            TripPlanItemType type,
            Long id,
            String name,
            String category,
            Double latitude,
            Double longitude,
            int stayMinutes,
            String reason
    ) {
        OptionalLong toCandidate = routeMinutes(
                current.latitude(), current.longitude(), latitude, longitude
        );
        if (toCandidate.isEmpty()) {
            return null;
        }

        LocalDateTime startAt = currentEnd.plusMinutes(toCandidate.getAsLong());
        LocalDateTime endAt = startAt.plusMinutes(stayMinutes);
        long minutesToAirportTarget;

        TripRentalSelection rental = trip.getSelectedRental();
        if (trip.getLocalTransportMode() == LocalTransportMode.RENTAL_CAR && rental != null) {
            OptionalLong toRental = routeMinutes(
                    latitude, longitude, rental.getLatitude(), rental.getLongitude()
            );
            if (toRental.isEmpty()) {
                return null;
            }
            minutesToAirportTarget = toRental.getAsLong() + FlightTimePolicy.RENTAL_RETURN_MINUTES
                    + Math.max(0, rental.getEstimatedShuttleMinutes());
        } else {
            OptionalLong toAirport = routeMinutes(
                    latitude, longitude, airport.latitude(), airport.longitude()
            );
            if (toAirport.isEmpty()) {
                return null;
            }
            minutesToAirportTarget = toAirport.getAsLong();
        }

        if (endAt.plusMinutes(minutesToAirportTarget).isAfter(airport.startAt())) {
            return null;
        }

        TripPlanItemResponse item = new TripPlanItemResponse(
                0,
                type,
                id,
                null,
                name,
                category,
                latitude,
                longitude,
                startAt,
                endAt,
                stayMinutes,
                localSegmentMode(trip),
                reason
        );
        return new CandidateVisit(item);
    }

    private OptionalLong actualTravelMinutes(
            Trip trip,
            TripPlanItemResponse origin,
            TripPlanItemResponse destination
    ) {
        TripRentalSelection rental = trip.getSelectedRental();
        if (trip.getLocalTransportMode() == LocalTransportMode.RENTAL_CAR
                && rental != null
                && "ARRIVAL_AIRPORT".equals(origin.category())) {
            OptionalLong route = routeMinutes(
                    rental.getLatitude(), rental.getLongitude(),
                    destination.latitude(), destination.longitude()
            );
            return route.isEmpty()
                    ? OptionalLong.empty()
                    : OptionalLong.of(route.getAsLong() + FlightTimePolicy.RENTAL_PICKUP_MINUTES + Math.max(0, rental.getEstimatedShuttleMinutes()));
        }

        return routeMinutes(
                origin.latitude(), origin.longitude(),
                destination.latitude(), destination.longitude()
        );
    }

    /** 실제 Kakao 경로 조회에 실패하면 0분으로 간주하지 않고 해당 후보를 사용하지 않는다. */
    private OptionalLong routeMinutes(
            Double originLatitude,
            Double originLongitude,
            Double destinationLatitude,
            Double destinationLongitude
    ) {
        if (originLatitude == null || originLongitude == null
                || destinationLatitude == null || destinationLongitude == null) {
            return OptionalLong.empty();
        }

        if (routingService.isSameLocation(
                originLatitude, originLongitude, destinationLatitude, destinationLongitude)) {
            return OptionalLong.of(0L);
        }

        try {
            DrivingRouteResult route = routingService.findDrivingRoute(
                    originLatitude,
                    originLongitude,
                    destinationLatitude,
                    destinationLongitude
            );
            return OptionalLong.of(Math.max(1L, (long) Math.ceil(route.durationSeconds() / 60.0)));
        } catch (RuntimeException exception) {
            log.warn(
                    "일정 feasibility용 Kakao 경로 조회 실패. ({}, {}) -> ({}, {}): {}",
                    originLatitude,
                    originLongitude,
                    destinationLatitude,
                    destinationLongitude,
                    exception.getMessage()
            );
            return OptionalLong.empty();
        }
    }

    private Set<String> collectUsedPlaceKeys(List<TripPlanDayResponse> days) {
        Set<String> result = new HashSet<>();
        for (TripPlanDayResponse day : days) {
            for (TripPlanItemResponse item : day.items()) {
                if (item.placeId() != null) {
                    result.add(placeKey(item.type(), item.placeId()));
                }
            }
        }
        return result;
    }

    private String placeKey(TripPlanItemType type, Long id) {
        return type.name() + ":" + id;
    }

    private int findCategoryIndex(List<TripPlanItemResponse> items, String category) {
        for (int i = 0; i < items.size(); i++) {
            if (category.equals(items.get(i).category())) {
                return i;
            }
        }
        return -1;
    }

    private LocalDateTime endOrStart(TripPlanItemResponse item) {
        return item.endAt() != null ? item.endAt() : item.startAt();
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private SegmentTransportMode localSegmentMode(Trip trip) {
        return SegmentTransportMode.valueOf(trip.getLocalTransportMode().name());
    }

    private double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    private Comparator<TripPlanItemResponse> itemTimeComparator() {
        return Comparator.comparing(
                item -> item.startAt() == null ? LocalDateTime.MAX : item.startAt()
        );
    }

    private TripPlanDayResponse copyDay(
            TripPlanDayResponse day,
            List<TripPlanItemResponse> items
    ) {
        return new TripPlanDayResponse(day.dayNumber(), day.date(), items, List.of());
    }

    private TripPlanItemResponse copyWithTimesAndReason(
            TripPlanItemResponse item,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Integer stayMinutes,
            String reason
    ) {
        return new TripPlanItemResponse(
                item.order(), item.type(), item.placeId(), item.referenceId(),
                item.name(), item.category(), item.latitude(), item.longitude(),
                startAt, endAt, stayMinutes, item.transportModeFromPrevious(), reason
        );
    }

    private List<TripPlanItemResponse> applyOrders(List<TripPlanItemResponse> items) {
        List<TripPlanItemResponse> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            TripPlanItemResponse item = items.get(i);
            result.add(new TripPlanItemResponse(
                    i + 1, item.type(), item.placeId(), item.referenceId(),
                    item.name(), item.category(), item.latitude(), item.longitude(),
                    item.startAt(), item.endAt(), item.stayMinutes(),
                    item.transportModeFromPrevious(), item.reason()
            ));
        }
        return result;
    }

    private enum MealSlot {
        BREAKFAST(BREAKFAST_AT, "아침 식사 적합도와 품질을 검증한 일정입니다."),
        LUNCH(LUNCH_AT, "점심 식사 적합도와 품질을 검증한 일정입니다."),
        DINNER(DINNER_AT, "저녁 식사 적합도와 품질을 검증한 일정입니다.");

        private final LocalTime targetTime;
        private final String reason;

        MealSlot(LocalTime targetTime, String reason) {
            this.targetTime = targetTime;
            this.reason = reason;
        }
    }

    private enum MealRole {
        BREAKFAST,
        LUNCH,
        DINNER,
        OUTSIDE_SLOT;

        private static MealRole from(LocalTime time) {
            if (!time.isBefore(LocalTime.of(7, 0)) && time.isBefore(LocalTime.of(10, 30))) {
                return BREAKFAST;
            }
            if (!time.isBefore(LocalTime.of(11, 0)) && time.isBefore(LocalTime.of(15, 0))) {
                return LUNCH;
            }
            if (!time.isBefore(LocalTime.of(17, 0)) && time.isBefore(LocalTime.of(21, 30))) {
                return DINNER;
            }
            return OUTSIDE_SLOT;
        }
    }

    private record CandidateVisit(TripPlanItemResponse item) {
    }
}
