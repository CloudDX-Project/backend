package com.travel.trip.plan.async;

import com.travel.trip.entity.LocalTransportMode;
import com.travel.trip.entity.MainTransportMode;
import com.travel.trip.plan.dto.TripPlanResponse;

import java.util.List;

final class TripPlanAsyncTestFixtures {

    private TripPlanAsyncTestFixtures() {
    }

    static TripPlanResponse completedPlan() {
        return new TripPlanResponse(
                3L,
                "AMAZON_BEDROCK",
                "KAKAO_MOBILITY_ROUTING_WITH_FALLBACK",
                MainTransportMode.AIR,
                LocalTransportMode.RENTAL_CAR,
                null,
                null,
                null,
                null,
                List.of(),
                0,
                0,
                0,
                List.of()
        );
    }
}
