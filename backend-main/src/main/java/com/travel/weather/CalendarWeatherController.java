package com.travel.weather;

import com.travel.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "날씨", description = "여행지 좌표와 여행 정보를 기준으로 단기·중기 일별 날씨를 제공합니다.")
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class CalendarWeatherController {

    private final WeatherService weatherService;

    @GetMapping("/calendar")
    @Operation(summary = "여행지 달력 날씨 조회", description = "목적지 이름과 좌표를 기준으로 달력에 표시할 일별 날씨 정보를 조회합니다.")
    public ApiResponse<List<DailyWeatherResponse>> getCalendarWeather(

            @RequestParam
            String destination,

            @RequestParam
            double latitude,

            @RequestParam
            double longitude
    ) {

        return ApiResponse.success(
                weatherService.getCalendarWeather(
                        destination,
                        latitude,
                        longitude
                )
        );
    }
}
