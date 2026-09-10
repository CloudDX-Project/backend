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
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WeatherService {

    private static final ZoneId KOREA_ZONE =
            ZoneId.of("Asia/Seoul");

    /*
     * 현재 프로젝트 정책:
     * 오늘부터 10일 후까지 달력 예보 표시
     */
    private static final int CALENDAR_FORECAST_DAYS = 10;

    private final TripRepository tripRepository;

    private final KmaShortWeatherClient shortWeatherClient;

    private final KmaMidWeatherClient midWeatherClient;


    /*
     * ============================================
     * 기존 Trip 날씨 조회
     * ============================================
     */
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

        return getWeather(
                trip.getDestination(),
                trip.getDestinationLatitude(),
                trip.getDestinationLongitude(),
                trip.getStartDate(),
                trip.getEndDate()
        );
    }


    /*
     * ============================================
     * 달력용 날씨 조회
     *
     * Trip 생성 전에도 호출 가능
     * 오늘 ~ 오늘 + 10일
     * ============================================
     */
    public List<DailyWeatherResponse> getCalendarWeather(

            String destination,

            double latitude,

            double longitude
    ) {

        LocalDate today =
                LocalDate.now(
                        KOREA_ZONE
                );

        LocalDate endDate =
                today.plusDays(
                        CALENDAR_FORECAST_DAYS
                );

        return getWeather(
                destination,
                latitude,
                longitude,
                today,
                endDate
        );
    }


    /*
     * ============================================
     * 공통 날씨 조회
     * ============================================
     */
    private List<DailyWeatherResponse> getWeather(

            String destination,

            double latitude,

            double longitude,

            LocalDate startDate,

            LocalDate endDate
    ) {

        Map<LocalDate, WeatherCondition>
                weatherByDate =
                new HashMap<>();


        /*
         * 가까운 날짜
         * → 단기예보 우선
         */
        weatherByDate.putAll(

                shortWeatherClient.getDailyWeather(
                        latitude,
                        longitude,
                        startDate,
                        endDate
                )
        );


        /*
         * 단기예보로 채우지 못한 날짜
         * → 중기예보
         */
        midWeatherClient
                .getDailyWeather(
                        destination,
                        startDate,
                        endDate
                )
                .forEach(
                        weatherByDate::putIfAbsent
                );


        /*
         * 예보가 실제로 존재하는 날짜만 반환
         *
         * 달력에서 UNKNOWN 아이콘을
         * 굳이 표시하지 않도록 함.
         */
        return startDate

                .datesUntil(
                        endDate.plusDays(1)
                )

                .filter(
                        weatherByDate::containsKey
                )

                .map(date ->
                        new DailyWeatherResponse(
                                date,
                                weatherByDate.get(date)
                        )
                )

                .toList();
    }
}