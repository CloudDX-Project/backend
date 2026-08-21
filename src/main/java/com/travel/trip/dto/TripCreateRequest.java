package com.travel.trip.dto;

import com.travel.trip.entity.TransportType;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record TripCreateRequest(

        @NotBlank(message = "출발지는 필수입니다.")
        String departure,

        @NotBlank(message = "목적지는 필수입니다.")
        String destination,

        @NotNull(message = "출발일은 필수입니다.")
        @FutureOrPresent(message = "출발일은 오늘 이후여야 합니다.")
        LocalDate startDate,

        @NotNull(message = "종료일은 필수입니다.")
        @FutureOrPresent(message = "종료일은 오늘 이후여야 합니다.")
        LocalDate endDate,

        @Min(value = 1, message = "인원수는 1명 이상이어야 합니다.")
        int peopleCount,

        @NotNull(message = "예산은 필수입니다.")
        @PositiveOrZero(message = "예산은 0원 이상이어야 합니다.")
        Long budget,

        @NotNull(message = "1인당 하루 식비는 필수입니다.")
        @PositiveOrZero(message = "1인당 하루 식비는 0원 이상이어야 합니다.")
        Long mealBudgetPerPersonPerDay,

        @NotNull(message = "교통수단은 필수입니다.")
        TransportType transportType

) {
}