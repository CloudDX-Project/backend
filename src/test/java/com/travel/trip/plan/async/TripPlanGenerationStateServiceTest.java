package com.travel.trip.plan.async;

import com.travel.global.exception.BusinessException;
import com.travel.global.exception.ErrorCode;
import com.travel.trip.entity.Trip;
import com.travel.trip.plan.dto.TripPlanResponse;
import com.travel.trip.repository.TripRepository;
import com.travel.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.travel.trip.plan.async.TripPlanAsyncTestFixtures.completedPlan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TripPlanGenerationStateServiceTest {

    private TripRepository tripRepository;
    private TripPlanGenerationRepository generationRepository;
    private TripPlanGenerationStateService service;
    private Trip trip;

    @BeforeEach
    void setUp() {
        tripRepository = mock(TripRepository.class);
        generationRepository = mock(TripPlanGenerationRepository.class);
        JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
        service = new TripPlanGenerationStateService(
                tripRepository,
                generationRepository,
                jsonMapper
        );
        trip = mock(Trip.class);
        User user = mock(User.class);
        when(trip.getId()).thenReturn(3L);
        when(trip.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(2L);
        when(tripRepository.findByIdAndUserId(3L, 2L)).thenReturn(Optional.of(trip));
        when(tripRepository.findOwnedForUpdate(3L, 2L)).thenReturn(Optional.of(trip));
    }

    @Test
    void preparesNewPendingRequest() {
        when(generationRepository.findByTrip_Id(3L)).thenReturn(Optional.empty());
        when(generationRepository.saveAndFlush(any(TripPlanGeneration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TripPlanGenerationStateService.PreparedRequest prepared = service.prepare(2L, 3L);

        assertThat(prepared.shouldPublish()).isTrue();
        assertThat(prepared.response().status()).isEqualTo(TripPlanGenerationStatus.PENDING);
        assertThat(prepared.message().tripId()).isEqualTo(3L);
        assertThat(prepared.message().userId()).isEqualTo(2L);
    }

    @Test
    void returnsExistingActiveRequestWithoutRepublishing() {
        TripPlanGeneration generation = TripPlanGeneration.pending(
                trip,
                "request-1",
                LocalDateTime.now()
        );
        generation.markProcessing(LocalDateTime.now());
        when(generationRepository.findByTrip_Id(3L)).thenReturn(Optional.of(generation));

        TripPlanGenerationStateService.PreparedRequest prepared = service.prepare(2L, 3L);

        assertThat(prepared.shouldPublish()).isFalse();
        assertThat(prepared.message()).isNull();
        assertThat(prepared.response().status()).isEqualTo(TripPlanGenerationStatus.PROCESSING);
    }

    @Test
    void storesAndReadsCompletedPlan() {
        TripPlanGeneration generation = TripPlanGeneration.pending(
                trip,
                "request-1",
                LocalDateTime.now()
        );
        TripPlanQueueMessage message = new TripPlanQueueMessage("request-1", 2L, 3L);
        when(generationRepository.findByTrip_Id(3L)).thenReturn(Optional.of(generation));

        assertThat(service.markProcessing(message)).isTrue();

        TripPlanResponse plan = completedPlan();
        service.markCompleted(message, plan);
        var response = service.getStatus(2L, 3L);

        assertThat(response.status()).isEqualTo(TripPlanGenerationStatus.COMPLETED);
        assertThat(response.plan().tripId()).isEqualTo(3L);
        assertThat(response.errorMessage()).isNull();
    }

    @Test
    void rejectsUnknownOwnerAndMissingJob() {
        when(tripRepository.findOwnedForUpdate(3L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.prepare(99L, 3L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.TRIP_NOT_FOUND)
                );

        when(generationRepository.findByTrip_Id(3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getStatus(2L, 3L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.TRIP_PLAN_REQUEST_NOT_FOUND)
                );
    }

    @Test
    void ignoresStaleRequestId() {
        TripPlanGeneration generation = TripPlanGeneration.pending(
                trip,
                "current-request",
                LocalDateTime.now()
        );
        when(generationRepository.findByTrip_Id(3L)).thenReturn(Optional.of(generation));

        assertThat(service.markProcessing(
                new TripPlanQueueMessage("stale-request", 2L, 3L)
        )).isFalse();
    }

}
