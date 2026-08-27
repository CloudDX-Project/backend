package com.travel.trip.dto;

import com.travel.trip.entity.SegmentTransportMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record TransportSegmentCreateRequest(

        @NotNull(
                message = "이동 순서는 필수입니다."
        )
        @Min(
                value = 1,
                message = "이동 순서는 1 이상이어야 합니다."
        )
        Integer sequence,

        @NotNull(
                message = "교통수단은 필수입니다."
        )
        SegmentTransportMode mode,

        @NotBlank(
                message = "출발지는 필수입니다."
        )
        String departureName,

        @NotBlank(
                message = "도착지는 필수입니다."
        )
        String arrivalName,

        Double departureLatitude,

        Double departureLongitude,

        Double arrivalLatitude,

        Double arrivalLongitude,

        LocalDateTime departureAt,

        LocalDateTime arrivalAt

) {
}