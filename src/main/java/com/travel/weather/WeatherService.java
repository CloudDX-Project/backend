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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

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
         * 서로 독립적인 기상청 단기·중기예보를 동시에 조회한다.
         * 첫 응답 시간은 두 외부 호출의 합이 아니라
         * 더 오래 걸린 한 호출 수준으로 줄어든다.
         */
        CompletableFuture<Map<LocalDate, WeatherCondition>>
                shortWeatherFuture =
                CompletableFuture.supplyAsync(() ->
                        shortWeatherClient.getDailyWeather(
                                latitude,
                                longitude,
                                startDate,
                                endDate
                        )
                );

        CompletableFuture<Map<LocalDate, WeatherCondition>>
                midWeatherFuture =
                CompletableFuture.supplyAsync(() ->
                        midWeatherClient.getDailyWeather(
                                destination,
                                startDate,
                                endDate
                        )
                );

        /*
         * 가까운 날짜는 단기예보를 우선하고,
         * 단기예보로 채우지 못한 날짜만 중기예보로 보완한다.
         */
        weatherByDate.putAll(
                awaitWeather(shortWeatherFuture)
        );

        awaitWeather(midWeatherFuture)
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


    /*
     * CompletableFuture가 외부 API 예외를 감싸더라도
     * 기존 동기 호출과 같은 예외 타입을 유지한다.
     */
    private <T> T awaitWeather(
            CompletableFuture<T> future
    ) {

        try {

            return future.join();

        } catch (CompletionException e) {

            if (e.getCause() instanceof RuntimeException cause) {

                throw cause;
            }

            throw e;
        }
    }
}
