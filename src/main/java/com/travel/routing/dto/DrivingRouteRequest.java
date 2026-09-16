package com.travel.routing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record DrivingRouteRequest(

        String originName,

        @NotNull(message = "출발지 위도는 필수입니다.")
        @DecimalMin(value = "-90.0", message = "출발지 위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90.0", message = "출발지 위도는 90 이하여야 합니다.")
        Double originLatitude,

        @NotNull(message = "출발지 경도는 필수입니다.")
        @DecimalMin(value = "-180.0", message = "출발지 경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180.0", message = "출발지 경도는 180 이하여야 합니다.")
        Double originLongitude,

        String destinationName,

        @NotNull(message = "도착지 위도는 필수입니다.")
        @DecimalMin(value = "-90.0", message = "도착지 위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90.0", message = "도착지 위도는 90 이하여야 합니다.")
        Double destinationLatitude,

        @NotNull(message = "도착지 경도는 필수입니다.")
        @DecimalMin(value = "-180.0", message = "도착지 경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180.0", message = "도착지 경도는 180 이하여야 합니다.")
        Double destinationLongitude
) {
}
