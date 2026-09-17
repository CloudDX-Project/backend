package com.travel.trip.plan.async;

import com.travel.global.exception.BusinessException;
import com.travel.global.exception.ErrorCode;
import com.travel.trip.plan.dto.TripPlanRequestResponse;
import com.travel.trip.plan.dto.TripPlanStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripPlanRequestService {

    private final TripPlanGenerationStateService stateService;
    private final TripPlanQueuePublisher queuePublisher;

    public TripPlanRequestResponse requestPlan(
            Long userId,
            Long tripId
    ) {
        TripPlanGenerationStateService.PreparedRequest prepared =
                stateService.prepare(userId, tripId);

        if (!prepared.shouldPublish()) {
            return prepared.response();
        }

        try {
            queuePublisher.publish(prepared.message());
            return prepared.response();
        } catch (RuntimeException e) {
            log.error(
                    "여행 일정 SQS 전송 실패. tripId={}, requestId={}",
                    tripId,
                    prepared.response().requestId(),
                    e
            );
            stateService.markFailed(
                    prepared.message(),
                    "여행 일정 생성 요청을 전달하지 못했습니다."
            );
            throw new BusinessException(ErrorCode.TRIP_PLAN_QUEUE_UNAVAILABLE);
        }
    }

    public TripPlanStatusResponse getStatus(
            Long userId,
            Long tripId
    ) {
        return stateService.getStatus(userId, tripId);
    }
}
