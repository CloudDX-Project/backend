package com.travel.trip.plan.async;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripPlanQueuePublisherTest {

    @Test
    void sendsSerializedMessageToConfiguredQueue() {
        SqsClient sqsClient = mock(SqsClient.class);
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("message-1").build());

        TripPlanQueuePublisher publisher = new TripPlanQueuePublisher(
                sqsClient,
                JsonMapper.builder().findAndAddModules().build()
        );
        ReflectionTestUtils.setField(publisher, "queueUrl", "https://sqs.example/plan");

        publisher.publish(new TripPlanQueueMessage("request-1", 2L, 3L));

        var captor = org.mockito.ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(captor.capture());
        assertThat(captor.getValue().queueUrl()).isEqualTo("https://sqs.example/plan");
        assertThat(captor.getValue().messageBody()).contains("request-1", "\"tripId\":3");
    }

    @Test
    void rejectsMissingQueueUrl() {
        TripPlanQueuePublisher publisher = new TripPlanQueuePublisher(
                mock(SqsClient.class),
                JsonMapper.builder().findAndAddModules().build()
        );
        ReflectionTestUtils.setField(publisher, "queueUrl", " ");

        assertThatThrownBy(() -> publisher.publish(
                new TripPlanQueueMessage("request-1", 2L, 3L)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TRIP_PLAN_QUEUE_URL");
    }
}
