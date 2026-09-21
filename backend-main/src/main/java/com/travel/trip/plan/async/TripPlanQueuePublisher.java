package com.travel.trip.plan.async;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Component
@RequiredArgsConstructor
public class TripPlanQueuePublisher {

    private final SqsClient sqsClient;
    private final JsonMapper jsonMapper;

    @Value("${aws.sqs.trip-plan.queue-url:}")
    private String queueUrl;

    public void publish(TripPlanQueueMessage message) {
        validateQueueUrl();

        try {
            sqsClient.sendMessage(
                    SendMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .messageBody(jsonMapper.writeValueAsString(message))
                            .build()
            );
        } catch (JacksonException e) {
            throw new IllegalStateException("여행 일정 요청 메시지를 만들 수 없습니다.", e);
        }
    }

    private void validateQueueUrl() {
        if (queueUrl == null || queueUrl.isBlank()) {
            throw new IllegalStateException("TRIP_PLAN_QUEUE_URL이 설정되지 않았습니다.");
        }
    }
}
