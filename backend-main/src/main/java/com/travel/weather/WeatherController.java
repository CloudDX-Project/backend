package com.travel.weather;

import com.travel.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "여행 날씨", description = "저장된 여행의 목적지와 날짜를 기준으로 일자별 예보를 조회합니다.")
@RestController
@RequestMapping("/api/trips/{tripId}/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    @GetMapping
    @Operation(summary = "여행 일정 날씨 조회", description = "본인 여행의 목적지 좌표와 여행 기간을 기준으로 일정에 사용할 일별 예보를 반환합니다.")
    public ApiResponse<List<DailyWeatherResponse>> getTripWeather(
            Authentication authentication,
            @PathVariable Long tripId
    ) {

        Long userId =
                (Long) authentication.getPrincipal();

        return ApiResponse.success(
                weatherService.getTripWeather(
                        userId,
                        tripId
                )
        );
    }
}
