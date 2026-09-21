package com.travel.trip.plan.async;

import com.travel.global.exception.BusinessException;
import com.travel.global.exception.ErrorCode;
import com.travel.trip.entity.Trip;
import com.travel.trip.plan.dto.TripPlanRequestResponse;
import com.travel.trip.plan.dto.TripPlanResponse;
import com.travel.trip.plan.dto.TripPlanStatusResponse;
import com.travel.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TripPlanGenerationStateService {

    private final TripRepository tripRepository;
    private final TripPlanGenerationRepository generationRepository;
    private final JsonMapper jsonMapper;

    @Transactional
    public PreparedRequest prepare(
            Long userId,
            Long tripId
    ) {
        Trip trip = tripRepository.findOwnedForUpdate(tripId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));

        TripPlanGeneration generation = generationRepository.findByTrip_Id(tripId)
                .orElse(null);

        if (generation != null && generation.isActive()) {
            return new PreparedRequest(
                    new TripPlanRequestResponse(
                            tripId,
                            generation.getRequestId(),
                            generation.getStatus()
                    ),
                    null,
                    false
            );
        }

        String requestId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        if (generation == null) {
            generation = TripPlanGeneration.pending(trip, requestId, now);
        } else {
            generation.restart(requestId, now);
        }

        generationRepository.saveAndFlush(generation);

        TripPlanQueueMessage message = new TripPlanQueueMessage(
                requestId,
                userId,
                tripId
        );

        return new PreparedRequest(
                new TripPlanRequestResponse(
                        tripId,
                        requestId,
                        TripPlanGenerationStatus.PENDING
                ),
                message,
                true
        );
    }

    @Transactional
    public boolean markProcessing(TripPlanQueueMessage message) {
        TripPlanGeneration generation = findMatching(message);

        if (generation == null
                || generation.getStatus() == TripPlanGenerationStatus.COMPLETED
                || generation.getStatus() == TripPlanGenerationStatus.FAILED) {
            return false;
        }

        generation.markProcessing(LocalDateTime.now());
        return true;
    }

    @Transactional
    public void markCompleted(
            TripPlanQueueMessage message,
            TripPlanResponse response
    ) {
        TripPlanGeneration generation = requireMatching(message);

        try {
            generation.markCompleted(
                    jsonMapper.writeValueAsString(response),
                    LocalDateTime.now()
            );
        } catch (JacksonException e) {
            throw new IllegalStateException("생성된 여행 일정을 저장할 수 없습니다.", e);
        }
    }

    @Transactional
    public void markFailed(
            TripPlanQueueMessage message,
            String errorMessage
    ) {
        TripPlanGeneration generation = findMatching(message);
        if (generation != null) {
            generation.markFailed(errorMessage, LocalDateTime.now());
        }
    }

    @Transactional(readOnly = true)
    public TripPlanStatusResponse getStatus(
            Long userId,
            Long tripId
    ) {
        tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));

        TripPlanGeneration generation = generationRepository.findByTrip_Id(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_PLAN_REQUEST_NOT_FOUND));

        return new TripPlanStatusResponse(
                tripId,
                generation.getRequestId(),
                generation.getStatus(),
                deserializePlan(generation),
                generation.getErrorMessage(),
                generation.getRequestedAt(),
                generation.getStartedAt(),
                generation.getCompletedAt()
        );
    }

    private TripPlanResponse deserializePlan(TripPlanGeneration generation) {
        if (generation.getStatus() != TripPlanGenerationStatus.COMPLETED
                || generation.getResultJson() == null) {
            return null;
        }

        try {
            return jsonMapper.readValue(
                    generation.getResultJson(),
                    TripPlanResponse.class
            );
        } catch (JacksonException e) {
            throw new IllegalStateException("저장된 여행 일정 결과를 읽을 수 없습니다.", e);
        }
    }

    private TripPlanGeneration requireMatching(TripPlanQueueMessage message) {
        TripPlanGeneration generation = findMatching(message);
        if (generation == null) {
            throw new IllegalStateException("현재 요청과 일치하는 여행 일정 작업이 없습니다.");
        }
        return generation;
    }

    private TripPlanGeneration findMatching(TripPlanQueueMessage message) {
        return generationRepository.findByTrip_Id(message.tripId())
                .filter(generation -> generation.matches(message.requestId()))
                .filter(generation -> Objects.equals(
                        generation.getTrip().getUser().getId(),
                        message.userId()
                ))
                .orElse(null);
    }

    public record PreparedRequest(
            TripPlanRequestResponse response,
            TripPlanQueueMessage message,
            boolean shouldPublish
    ) {
    }
}
