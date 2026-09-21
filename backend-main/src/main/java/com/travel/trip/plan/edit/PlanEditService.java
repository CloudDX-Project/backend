package com.travel.trip.plan.edit;

import com.travel.global.exception.BusinessException;
import com.travel.global.exception.ErrorCode;
import com.travel.routing.service.RoutingService;
import com.travel.trip.entity.LocalTransportMode;
import com.travel.trip.entity.MainTransportMode;
import com.travel.trip.entity.Trip;
import com.travel.trip.plan.async.*;
import com.travel.trip.plan.dto.*;
import com.travel.trip.plan.service.TripPlanService;
import com.travel.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PlanEditService {
    private final TripRepository trips;
    private final TripPlanGenerationRepository generations;
    private final JsonMapper mapper;
    private final PlanEditPolicy policy;
    private final PlanEditScheduler scheduler;
    private final TripPlanService planStorage;
    private final RoutingService routing;

    @Transactional(readOnly = true)
    public PlanEditResponse get(Long userId, Long tripId) {
        trips.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        var generation = completed(tripId);
        return response(generation, readPlan(generation));
    }

    @Transactional
    public PlanEditResponse save(Long userId, Long tripId, PlanEditRequest request) {
        // Same lock as prepare(): regeneration cannot start halfway through an edit.
        Trip trip = trips.findOwnedForUpdate(tripId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        var generation = completed(tripId);
        if (!Objects.equals(generation.getVersion(), request.baseRevision())
                || !generation.matches(request.requestId())) {
            throw new PlanEditException(HttpStatus.CONFLICT,
                    "다른 화면에서 일정이 변경되었습니다. 최신 일정을 다시 불러온 뒤 수정해주세요.");
        }
        if (trip.getMainTransportMode() != MainTransportMode.AIR
                || trip.getLocalTransportMode() != LocalTransportMode.RENTAL_CAR
                || trip.getSelectedRental() == null) {
            throw PlanEditException.invalid("현재 일정 편집은 항공 + 렌터카 여행을 지원합니다.");
        }
        var before = readPlan(generation);
        var source = policy.validate(before.days(), request);
        return routing.withSnapshot(() -> {
            List<TripPlanDayResponse> changed = new ArrayList<>();
            for (int i = 0; i < before.days().size(); i++) {
                var day = before.days().get(i);
                var draft = request.days().get(i);
                if (!unchanged(day, draft)) changed.add(scheduler.schedule(trip, day, draft, source));
            }
            if (changed.isEmpty()) return response(generation, before);
            var updated = planStorage.persistEditedPlan(trip, before, changed);
            // Result JSON, items and route segments commit or roll back together.
            generation.replaceCompletedResult(writePlan(updated), LocalDateTime.now());
            generations.flush();
            return response(generation, updated);
        });
    }

    static boolean unchanged(TripPlanDayResponse before, PlanEditRequest.Day after) {
        if (before.items().size() != after.items().size()) return false;
        for (int i = 0; i < before.items().size(); i++) {
            var item = before.items().get(i);
            var spec = after.items().get(i);
            if (!Objects.equals(PlanEditPolicy.key(before.dayNumber(), item.order()), spec.itemKey())
                    || spec.replacement() != null) return false;
            if (PlanEditPolicy.editable(item)) {
                String time = item.startAt() == null ? null : item.startAt().format(DateTimeFormatter.ofPattern("HH:mm"));
                if (!Objects.equals(item.stayMinutes(), spec.stayMinutes()) || !Objects.equals(time, spec.startTime())) return false;
            }
        }
        return true;
    }

    private TripPlanGeneration completed(Long tripId) {
        var generation = generations.findByTrip_Id(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_PLAN_REQUEST_NOT_FOUND));
        if (generation.getStatus() != TripPlanGenerationStatus.COMPLETED || generation.getResultJson() == null) {
            throw new PlanEditException(HttpStatus.CONFLICT, "일정 생성이 완료된 뒤 편집해주세요.");
        }
        return generation;
    }

    private TripPlanResponse readPlan(TripPlanGeneration generation) {
        try {
            return mapper.readValue(generation.getResultJson(), TripPlanResponse.class);
        } catch (Exception exception) {
            throw new IllegalStateException("저장된 여행 일정 JSON을 읽을 수 없습니다.", exception);
        }
    }

    private String writePlan(TripPlanResponse plan) {
        try {
            return mapper.writeValueAsString(plan);
        } catch (Exception exception) {
            throw new IllegalStateException("수정한 여행 일정을 JSON으로 저장할 수 없습니다.", exception);
        }
    }

    private PlanEditResponse response(TripPlanGeneration generation, TripPlanResponse plan) {
        return new PlanEditResponse(plan.tripId(), generation.getRequestId(), generation.getVersion(), plan);
    }
}
