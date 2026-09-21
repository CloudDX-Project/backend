package com.travel.trip.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RentalCandidate(

        @NotBlank(message = "렌터카 ID는 필수입니다.")
        String id,

        @NotBlank(message = "렌터카 업체명은 필수입니다.")
        String company,

        String car,

        String pickup,

        @NotNull(message = "렌터카 업체 위도는 필수입니다.")
        @DecimalMin(
                value = "-90.0",
                message = "렌터카 업체 위도는 -90 이상이어야 합니다."
        )
        @DecimalMax(
                value = "90.0",
                message = "렌터카 업체 위도는 90 이하여야 합니다."
        )
        Double latitude,

        @NotNull(message = "렌터카 업체 경도는 필수입니다.")
        @DecimalMin(
                value = "-180.0",
                message = "렌터카 업체 경도는 -180 이상이어야 합니다."
        )
        @DecimalMax(
                value = "180.0",
                message = "렌터카 업체 경도는 180 이하여야 합니다."
        )
        Double longitude,

        @NotNull(message = "렌터카 셔틀 예상 소요시간은 필수입니다.")
        @Min(
                value = 1,
                message = "렌터카 셔틀 예상 소요시간은 1분 이상이어야 합니다."
        )
        Integer estimatedShuttleMinutes

) {
}