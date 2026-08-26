package com.travel.trip.dto;

import com.travel.trip.entity.TransportMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record TransportSegmentUpdateRequest(
        @NotNull(message = "교통수단은 필수입니다.")
        TransportMode mode,

        @NotBlank(message = "출발지는 필수입니다.")
        String departureName,

        @NotBlank(message = "도착지는 필수입니다.")
        String arrivalName,

        Double departureLatitude,
        Double departureLongitude,
        Double arrivalLatitude,
        Double arrivalLongitude,

        LocalDateTime departureAt,
        LocalDateTime arrivalAt
) {
}