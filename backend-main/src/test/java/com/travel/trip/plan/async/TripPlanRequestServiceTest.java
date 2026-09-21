package com.travel.trip.plan.async;

import com.travel.global.exception.BusinessException;
import com.travel.global.exception.ErrorCode;
import com.travel.trip.plan.dto.TripPlanRequestResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripPlanRequestServiceTest {

    private final TripPlanGenerationStateService stateService =
            mock(TripPlanGenerationStateService.class);
    private final TripPlanQueuePublisher publisher = mock(TripPlanQueuePublisher.class);
    private final TripPlanRequestService service =
            new TripPlanRequestService(stateService, publisher);

    @Test
    void publishesNewRequest() {
        TripPlanQueueMessage message = new TripPlanQueueMessage("request-1", 2L, 3L);
        TripPlanRequestResponse response = new TripPlanRequestResponse(
                3L,
                "request-1",
                TripPlanGenerationStatus.PENDING
        );
        when(stateService.prepare(2L, 3L)).thenReturn(
                new TripPlanGenerationStateService.PreparedRequest(response, message, true)
        );

        assertThat(service.requestPlan(2L, 3L)).isEqualTo(response);
        verify(publisher).publish(message);
    }

    @Test
    void reusesAlreadyActiveRequestWithoutPublishingDuplicateMessage() {
        TripPlanRequestResponse response = new TripPlanRequestResponse(
                3L,
                "request-1",
                TripPlanGenerationStatus.PROCESSING
        );
        when(stateService.prepare(2L, 3L)).thenReturn(
                new TripPlanGenerationStateService.PreparedRequest(response, null, false)
        );

        assertThat(service.requestPlan(2L, 3L)).isEqualTo(response);
        verify(publisher, never()).publish(any(TripPlanQueueMessage.class));
    }

    @Test
    void marksJobFailedWhenSqsPublishFails() {
        TripPlanQueueMessage message = new TripPlanQueueMessage("request-1", 2L, 3L);
        TripPlanRequestResponse response = new TripPlanRequestResponse(
                3L,
                "request-1",
                TripPlanGenerationStatus.PENDING
        );
        when(stateService.prepare(2L, 3L)).thenReturn(
                new TripPlanGenerationStateService.PreparedRequest(response, message, true)
        );
        doThrow(new IllegalStateException("sqs unavailable"))
                .when(publisher).publish(message);

        assertThatThrownBy(() -> service.requestPlan(2L, 3L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.TRIP_PLAN_QUEUE_UNAVAILABLE)
                );

        verify(stateService).markFailed(
                message,
                "여행 일정 생성 요청을 전달하지 못했습니다."
        );
    }
}
