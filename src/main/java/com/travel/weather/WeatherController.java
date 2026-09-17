package com.travel.weather;

import com.travel.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/trips/{tripId}/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    @GetMapping
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