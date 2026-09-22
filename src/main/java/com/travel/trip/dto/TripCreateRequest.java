package com.travel.trip.dto;

import com.travel.flight.dto.FlightCandidate;
import com.travel.trip.entity.FoodPreference;
import com.travel.trip.entity.LocalTransportMode;
import com.travel.trip.entity.MainTransportMode;
import com.travel.trip.entity.TripPace;
import com.travel.trip.entity.TripPreference;
import com.travel.trip.entity.VehicleFuelType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

public record TripCreateRequest(

        @NotBlank(message = "출발지는 필수입니다.")
        String departure,

        @NotNull(message = "출발지 위도는 필수입니다.")
        @DecimalMin(
                value = "-90.0",
                message = "출발지 위도는 -90 이상이어야 합니다."
        )
        @DecimalMax(
                value = "90.0",
                message = "출발지 위도는 90 이하여야 합니다."
        )
        Double departureLatitude,

        @NotNull(message = "출발지 경도는 필수입니다.")
        @DecimalMin(
                value = "-180.0",
                message = "출발지 경도는 -180 이상이어야 합니다."
        )
        @DecimalMax(
                value = "180.0",
                message = "출발지 경도는 180 이하여야 합니다."
        )
        Double departureLongitude,

        @NotBlank(message = "목적지는 필수입니다.")
        String destination,

        @NotNull(message = "목적지 위도는 필수입니다.")
        @DecimalMin(
                value = "-90.0",
                message = "목적지 위도는 -90 이상이어야 합니다."
        )
        @DecimalMax(
                value = "90.0",
                message = "목적지 위도는 90 이하여야 합니다."
        )
        Double destinationLatitude,

        @NotNull(message = "목적지 경도는 필수입니다.")
        @DecimalMin(
                value = "-180.0",
                message = "목적지 경도는 -180 이상이어야 합니다."
        )
        @DecimalMax(
                value = "180.0",
                message = "목적지 경도는 180 이하여야 합니다."
        )
        Double destinationLongitude,

        @NotNull(message = "출발일은 필수입니다.")
        @FutureOrPresent(
                message = "출발일은 오늘 이후여야 합니다."
        )
        LocalDate startDate,

        @NotNull(message = "여행 시작 시간은 필수입니다.")
        LocalTime startTime,

        @NotNull(message = "종료일은 필수입니다.")
        @FutureOrPresent(
                message = "종료일은 오늘 이후여야 합니다."
        )
        LocalDate endDate,

        @NotNull(message = "여행 종료 시간은 필수입니다.")
        LocalTime endTime,

        @Min(
                value = 1,
                message = "인원수는 1명 이상이어야 합니다."
        )
        int peopleCount,

        @NotNull(
                message = "목적지까지의 교통수단은 필수입니다."
        )
        MainTransportMode mainTransportMode,

        @NotNull(
                message = "현지 교통수단은 필수입니다."
        )
        LocalTransportMode localTransportMode,

        /*
         * 차량 이동 유류비 계산용.
         * 자동차/렌터카가 아니면 null이어도 된다.
         */
        VehicleFuelType fuelType,

        @DecimalMin(
                value = "1.0",
                message = "차량 연비는 1km/L 이상이어야 합니다."
        )
        @DecimalMax(
                value = "50.0",
                message = "차량 연비는 50km/L 이하여야 합니다."
        )
        Double vehicleEfficiencyKmpl,

        @NotNull(message = "예산은 필수입니다.")
        @PositiveOrZero(
                message = "예산은 0원 이상이어야 합니다."
        )
        Long budget,

        @NotNull(
                message = "1인당 하루 식비는 필수입니다."
        )
        @PositiveOrZero(
                message = "1인당 하루 식비는 0원 이상이어야 합니다."
        )
        Long mealBudgetPerPersonPerDay,

        @NotNull(message = "여행 속도는 필수입니다.")
        TripPace pace,

        @NotEmpty(
                message = "여행 테마는 1개 이상 선택해야 합니다."
        )
        @Size(
                max = 3,
                message = "여행 테마는 최대 3개까지 선택할 수 있습니다."
        )
        Set<TripPreference> preferences,

        @Size(
                max = 3,
                message = "음식 취향은 최대 3개까지 선택할 수 있습니다."
        )
        Set<FoodPreference> foodPreferences,

        /*
         * 자유 입력은 현재 "관광지 + N일차" 제약만 해석한다.
         * 그 외 문장은 일정 생성 규칙에 영향을 주지 않는다.
         */
        @Size(
                max = 1000,
                message = "여행 요청은 최대 1000자까지 입력할 수 있습니다."
        )
        String prompt,

        /*
         * 메인 화면에서 사용자가 직접 선택한 숙소.
         */
        @NotNull(message = "선택 숙소 ID는 필수입니다.")
        Long accommodationId,

        /*
         * AIR 여행이면 필수.
         */
        FlightCandidate outboundFlight,

        FlightCandidate returnFlight,

        /*
         * RENTAL_CAR 이용 시 필수.
         */
        @Valid
        RentalCandidate rental

) {
}