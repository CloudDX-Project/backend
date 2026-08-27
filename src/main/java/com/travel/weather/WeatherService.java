package com.travel.weather;

import com.travel.external.weather.KmaMidWeatherClient;
import com.travel.external.weather.KmaShortWeatherClient;
import com.travel.global.exception.BusinessException;
import com.travel.global.exception.ErrorCode;
import com.travel.trip.entity.Trip;
import com.travel.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WeatherService {

    private final TripRepository tripRepository;

    private final KmaShortWeatherClient
            shortWeatherClient;

    private final KmaMidWeatherClient
            midWeatherClient;


    public List<DailyWeatherResponse> getTripWeather(
            Long userId,
            Long tripId
    ) {

        Trip trip =
                tripRepository
                        .findByIdAndUserId(
                                tripId,
                                userId
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        ErrorCode.TRIP_NOT_FOUND
                                )
                        );


        Map<LocalDate, WeatherCondition>
                weatherByDate =
                new HashMap<>();


        /*
         * 1.
         * 가까운 날짜는 단기예보를 우선 사용
         */
        weatherByDate.putAll(

                shortWeatherClient.getDailyWeather(

                        trip.getDestinationLatitude(),

                        trip.getDestinationLongitude(),

                        trip.getStartDate(),

                        trip.getEndDate()
                )
        );


        /*
         * 2.
         * 단기예보에 없는 날짜만
         * 중기예보로 채운다.
         */
        midWeatherClient
                .getDailyWeather(

                        trip.getDestination(),

                        trip.getStartDate(),

                        trip.getEndDate()
                )
                .forEach(
                        weatherByDate::putIfAbsent
                );


        /*
         * 3.
         * 단기/중기 모두 없는 날짜는
         * UNKNOWN
         */
        return trip.getStartDate()

                .datesUntil(
                        trip.getEndDate()
                                .plusDays(1)
                )

                .map(date ->
                        new DailyWeatherResponse(

                                date,

                                weatherByDate
                                        .getOrDefault(
                                                date,
                                                WeatherCondition.UNKNOWN
                                        )
                        )
                )

                .toList();
    }
}