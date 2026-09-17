package com.travel.weather;

import com.travel.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class CalendarWeatherController {

    private final WeatherService weatherService;

    @GetMapping("/calendar")
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