package com.travel.trip.plan.async;

import com.travel.trip.plan.dto.TripPlanResponse;
import com.travel.trip.plan.service.TripPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import tools.jackson.databind.json.JsonMapper;

import static com.travel.trip.plan.async.TripPlanAsyncTestFixtures.completedPlan;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripPlanWorkerTest {

    private SqsClient sqsClient;
    private TripPlanService tripPlanService;
    private TripPlanGenerationStateService stateService;
    private TripPlanWorker worker;

    @BeforeEach
    void setUp() {
        sqsClient = mock(SqsClient.class);
        tripPlanService = mock(TripPlanService.class);
        stateService = mock(TripPlanGenerationStateService.class);
        worker = new TripPlanWorker(
                sqsClient,
                JsonMapper.builder().findAndAddModules().build(),
                tripPlanService,
                stateService
        );
        ReflectionTestUtils.setField(worker, "queueUrl", "https://sqs.example/plan");
        ReflectionTestUtils.setField(worker, "waitTimeSeconds", 1);
        ReflectionTestUtils.setField(worker, "visibilityTimeoutSeconds", 30);
        when(sqsClient.deleteMessage(any(DeleteMessageRequest.class)))
                .thenReturn(DeleteMessageResponse.builder().build());
    }

    @Test
    void generatesPlanAndDeletesMessage() {
        TripPlanQueueMessage queueMessage = new TripPlanQueueMessage("request-1", 2L, 3L);
        stubMessage("{\"requestId\":\"request-1\",\"userId\":2,\"tripId\":3}");
        when(stateService.markProcessing(queueMessage)).thenReturn(true);
        TripPlanResponse response = completedPlan();
        when(tripPlanService.createPlan(2L, 3L)).thenReturn(response);

        worker.poll();

        verify(stateService).markCompleted(queueMessage, response);
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void marksFailedPlanAndDeletesMessage() {
        TripPlanQueueMessage queueMessage = new TripPlanQueueMessage("request-1", 2L, 3L);
        stubMessage("{\"requestId\":\"request-1\",\"userId\":2,\"tripId\":3}");
        when(stateService.markProcessing(queueMessage)).thenReturn(true);
        doThrow(new IllegalStateException("bedrock failed"))
                .when(tripPlanService).createPlan(2L, 3L);

        worker.poll();

        verify(stateService).markFailed(
                queueMessage,
                "여행 일정 생성 중 오류가 발생했습니다."
        );
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void deletesMalformedMessage() {
        stubMessage("not-json");

        worker.poll();

        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void deletesAlreadyCompletedOrStaleMessage() {
        TripPlanQueueMessage queueMessage = new TripPlanQueueMessage("request-1", 2L, 3L);
        stubMessage("{\"requestId\":\"request-1\",\"userId\":2,\"tripId\":3}");
        when(stateService.markProcessing(queueMessage)).thenReturn(false);

        worker.poll();

        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    private void stubMessage(String body) {
        Message message = Message.builder()
                .body(body)
                .receiptHandle("receipt-1")
                .build();
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());
    }

}
