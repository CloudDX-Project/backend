package com.travel.trip.plan.async;

import com.travel.trip.entity.Trip;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TripPlanGenerationTest {

    @Test
    void movesFromPendingToProcessingAndCompleted() {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 9, 17, 10, 0);
        TripPlanGeneration generation = TripPlanGeneration.pending(
                mock(Trip.class),
                "request-1",
                requestedAt
        );

        assertThat(generation.getStatus()).isEqualTo(TripPlanGenerationStatus.PENDING);
        assertThat(generation.isActive()).isTrue();
        assertThat(generation.matches("request-1")).isTrue();

        generation.markProcessing(requestedAt.plusMinutes(1));
        generation.markCompleted("{\"tripId\":1}", requestedAt.plusMinutes(2));

        assertThat(generation.getStatus()).isEqualTo(TripPlanGenerationStatus.COMPLETED);
        assertThat(generation.getResultJson()).contains("tripId");
        assertThat(generation.isActive()).isFalse();
    }

    @Test
    void restartClearsPreviousResultAndFailureTruncatesLongMessage() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 17, 10, 0);
        TripPlanGeneration generation = TripPlanGeneration.pending(
                mock(Trip.class),
                "request-1",
                now
        );

        generation.markCompleted("result", now.plusMinutes(1));
        generation.restart("request-2", now.plusMinutes(2));

        assertThat(generation.getRequestId()).isEqualTo("request-2");
        assertThat(generation.getResultJson()).isNull();
        assertThat(generation.getStatus()).isEqualTo(TripPlanGenerationStatus.PENDING);

        generation.markFailed("x".repeat(1200), now.plusMinutes(3));

        assertThat(generation.getStatus()).isEqualTo(TripPlanGenerationStatus.FAILED);
        assertThat(generation.getErrorMessage()).hasSize(1000);
    }
}
