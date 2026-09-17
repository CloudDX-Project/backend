package com.travel.trip.plan.async;

import com.travel.global.exception.BusinessException;
import com.travel.trip.plan.dto.TripPlanResponse;
import com.travel.trip.plan.service.TripPlanService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.runtime-role",
        havingValue = "worker"
)
public class TripPlanWorker {

    private final SqsClient sqsClient;
    private final JsonMapper jsonMapper;
    private final TripPlanService tripPlanService;
    private final TripPlanGenerationStateService stateService;

    @Value("${aws.sqs.trip-plan.queue-url:}")
    private String queueUrl;

    @Value("${aws.sqs.trip-plan.wait-time-seconds:20}")
    private int waitTimeSeconds;

    @Value("${aws.sqs.trip-plan.visibility-timeout-seconds:900}")
    private int visibilityTimeoutSeconds;

    @PostConstruct
    void validateConfiguration() {
        if (queueUrl == null || queueUrl.isBlank()) {
            throw new IllegalStateException(
                    "Worker에는 TRIP_PLAN_QUEUE_URL 설정이 필요합니다."
            );
        }
    }

    @Scheduled(fixedDelayString = "${aws.sqs.trip-plan.poll-delay-ms:1000}")
    public void poll() {
        try {
            List<Message> messages = sqsClient.receiveMessage(
                    ReceiveMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .maxNumberOfMessages(1)
                            .waitTimeSeconds(waitTimeSeconds)
                            .visibilityTimeout(visibilityTimeoutSeconds)
                            .build()
            ).messages();

            for (Message message : messages) {
                process(message);
            }
        } catch (RuntimeException e) {
            log.error("여행 일정 Worker의 SQS polling 중 오류가 발생했습니다.", e);
        }
    }

    private void process(Message sqsMessage) {
        TripPlanQueueMessage message;

        try {
            message = jsonMapper.readValue(
                    sqsMessage.body(),
                    TripPlanQueueMessage.class
            );
        } catch (JacksonException e) {
            log.error("잘못된 여행 일정 SQS 메시지를 삭제합니다.", e);
            delete(sqsMessage);
            return;
        }

        if (!isValid(message)) {
            log.error("필수 값이 없는 여행 일정 SQS 메시지를 삭제합니다. message={}", message);
            delete(sqsMessage);
            return;
        }

        if (!stateService.markProcessing(message)) {
            log.info(
                    "이미 처리했거나 만료된 여행 일정 메시지를 건너뜁니다. tripId={}, requestId={}",
                    message.tripId(),
                    message.requestId()
            );
            delete(sqsMessage);
            return;
        }

        try {
            TripPlanResponse response = tripPlanService.createPlan(
                    message.userId(),
                    message.tripId()
            );

            stateService.markCompleted(message, response);
            delete(sqsMessage);

            log.info(
                    "여행 일정 비동기 생성 완료. tripId={}, requestId={}",
                    message.tripId(),
                    message.requestId()
            );
        } catch (RuntimeException e) {
            log.error(
                    "여행 일정 비동기 생성 실패. tripId={}, requestId={}",
                    message.tripId(),
                    message.requestId(),
                    e
            );
            stateService.markFailed(message, safeMessage(e));
            delete(sqsMessage);
        }
    }

    private boolean isValid(TripPlanQueueMessage message) {
        return message != null
                && message.requestId() != null
                && !message.requestId().isBlank()
                && message.userId() != null
                && message.tripId() != null;
    }

    private String safeMessage(RuntimeException exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException.getErrorCode().getMessage();
        }
        return "여행 일정 생성 중 오류가 발생했습니다.";
    }

    private void delete(Message message) {
        sqsClient.deleteMessage(
                DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build()
        );
    }
}
