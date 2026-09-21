package com.travel.trip.plan.dto;

import com.travel.flight.dto.FlightCandidate;
import jakarta.validation.constraints.NotNull;

public record TripPlanCreateRequest(

        @NotNull(message = "선택 숙소 ID는 필수입니다.")
        Long accommodationId,

        /*
         * AIR 여행인 경우 필수.
         * 프론트에서 /api/flights/search 응답 중
         * 사용자가 선택한 가는 편 객체를 그대로 전달한다.
         */
        FlightCandidate outboundFlight,

        /*
         * AIR 여행인 경우 필수.
         * 프론트에서 사용자가 선택한 오는 편 객체를 그대로 전달한다.
         */
        FlightCandidate returnFlight

) {
}