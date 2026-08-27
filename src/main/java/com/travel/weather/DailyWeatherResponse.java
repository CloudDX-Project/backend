package com.travel.weather;

import java.time.LocalDate;

public record DailyWeatherResponse(
        LocalDate date,
        WeatherCondition condition
) {
}