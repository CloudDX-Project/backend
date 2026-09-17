package com.travel.trip.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record TransportSegmentReorderRequest(
        @NotEmpty(message = "이동 구간 순서는 비어 있을 수 없습니다.")
        List<
                @Valid
                @NotNull(message = "이동 구간 ID는 null일 수 없습니다.")
                        Long
                > segmentIds
) {
}