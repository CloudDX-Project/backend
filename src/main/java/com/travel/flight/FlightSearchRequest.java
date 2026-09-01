package com.travel.flight;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record FlightSearchRequest(

        @NotBlank(message = "출발지는 필수입니다.")
        String departure,

        @NotBlank(message = "도착지는 필수입니다.")
        String destination,

        @NotNull(message = "여행 시작일은 필수입니다.")
        LocalDate startDate,

        @NotNull(message = "여행 시작시간은 필수입니다.")
        LocalTime startTime,

        @NotNull(message = "여행 종료일은 필수입니다.")
        LocalDate endDate,

        @NotNull(message = "여행 종료시간은 필수입니다.")
        LocalTime endTime,

        @Min(
                value = 1,
                message = "인원수는 1명 이상이어야 합니다."
        )
        int peopleCount

) {
}