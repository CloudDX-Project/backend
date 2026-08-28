package com.travel.external.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.travel.global.exception.BusinessException;
import com.travel.global.exception.ErrorCode;
import com.travel.weather.WeatherCondition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.cache.annotation.Cacheable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class KmaShortWeatherClient {

    private static final ZoneId KOREA_ZONE =
            ZoneId.of("Asia/Seoul");

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HHmm");

    /*
     * 우리는 하루의 대략적인 날씨만 필요하므로
     * 12:00 예보를 대표 날씨로 사용한다.
     */
    private static final String REPRESENTATIVE_TIME =
            "1200";

    /*
     * 기상청 단기예보 발표 시간
     */
    private static final List<LocalTime> BASE_TIMES =
            List.of(
                    LocalTime.of(2, 0),
                    LocalTime.of(5, 0),
                    LocalTime.of(8, 0),
                    LocalTime.of(11, 0),
                    LocalTime.of(14, 0),
                    LocalTime.of(17, 0),
                    LocalTime.of(20, 0),
                    LocalTime.of(23, 0)
            );

    private final RestClient restClient;
    private final String serviceKey;


    public KmaShortWeatherClient(

            @Value("${weather.kma.short.base-url}")
            String baseUrl,

            @Value("${weather.kma.short.service-key:}")
            String serviceKey
    ) {

        this.restClient =
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .build();

        this.serviceKey =
                serviceKey;
    }


    /*
     * ==================================================
     * 단기예보 조회
     * ==================================================
     */
    @Cacheable(
            value = "weatherShort",
            key = "#latitude + ':' + #longitude + ':' + #startDate + ':' + #endDate",
            unless = "#result == null || #result.isEmpty()"
    )
    public Map<LocalDate, WeatherCondition> getDailyWeather(

            double latitude,

            double longitude,

            LocalDate startDate,

            LocalDate endDate
    ) {

        LocalDate today =
                LocalDate.now(KOREA_ZONE);


        /*
         * 단기예보 범위를 아예 벗어나면
         * 외부 API 호출하지 않음.
         */
        if (
                endDate.isBefore(today)
                        ||
                        startDate.isAfter(
                                today.plusDays(3)
                        )
        ) {

            return Map.of();
        }


        validateServiceKey();


        /*
         * 위도/경도
         * →
         * 기상청 격자좌표 nx, ny
         */
        GridPoint grid =
                convertToGrid(
                        latitude,
                        longitude
                );


        /*
         * 가장 최근에 사용 가능한
         * 단기예보 발표 시각
         */
        BaseDateTime base =
                resolveBaseDateTime();


        /*
         * ================================
         * 개발 중 요청값 확인용
         * ================================
         *
         * serviceKey 자체는 절대 출력하지 않는다.
         */
        System.out.println(
                "===== KMA SHORT REQUEST ====="
        );

        System.out.println(
                "base_date = "
                        + base.date()
                        .format(DATE_FORMATTER)
        );

        System.out.println(
                "base_time = "
                        + base.time()
                        .format(TIME_FORMATTER)
        );

        System.out.println(
                "nx = " + grid.x()
        );

        System.out.println(
                "ny = " + grid.y()
        );

        System.out.println(
                "startDate = " + startDate
        );

        System.out.println(
                "endDate = " + endDate
        );

        System.out.println(
                "============================="
        );


        try {

            KmaShortResponse response =

                    restClient
                            .get()

                            .uri(uriBuilder ->
                                    uriBuilder

                                            .path(
                                                    "/getVilageFcst"
                                            )

                                            .queryParam(
                                                    "serviceKey",
                                                    serviceKey
                                            )

                                            .queryParam(
                                                    "pageNo",
                                                    1
                                            )

                                            /*
                                             * 2000 대신 1000 사용
                                             */
                                            .queryParam(
                                                    "numOfRows",
                                                    1000
                                            )

                                            .queryParam(
                                                    "dataType",
                                                    "JSON"
                                            )

                                            .queryParam(
                                                    "base_date",
                                                    base.date()
                                                            .format(
                                                                    DATE_FORMATTER
                                                            )
                                            )

                                            .queryParam(
                                                    "base_time",
                                                    base.time()
                                                            .format(
                                                                    TIME_FORMATTER
                                                            )
                                            )

                                            .queryParam(
                                                    "nx",
                                                    grid.x()
                                            )

                                            .queryParam(
                                                    "ny",
                                                    grid.y()
                                            )

                                            .build()
                            )

                            .retrieve()

                            .body(
                                    KmaShortResponse.class
                            );


            validateResponse(
                    response
            );


            List<Item> items =

                    response
                            .response()
                            .body()
                            .items()
                            .item();


            if (
                    items == null
                            ||
                            items.isEmpty()
            ) {

                return Map.of();
            }


            return toDailyWeather(

                    items,

                    startDate,

                    endDate
            );


        } catch (
                RestClientResponseException e
        ) {

            /*
             * HTTP 자체가
             * 400 / 401 / 403 / 500 등인 경우
             */

            System.out.println(
                    "===== KMA SHORT HTTP ERROR ====="
            );

            System.out.println(
                    "STATUS = "
                            + e.getStatusCode()
            );

            System.out.println(
                    "BODY = "
                            + e.getResponseBodyAsString()
            );

            System.out.println(
                    "================================"
            );


            throw new BusinessException(
                    ErrorCode.WEATHER_API_ERROR
            );


        } catch (
                RestClientException e
        ) {

            /*
             * 연결 실패 등
             */
            System.out.println(
                    "===== KMA SHORT CLIENT ERROR ====="
            );

            System.out.println(
                    "MESSAGE = "
                            + e.getMessage()
            );

            System.out.println(
                    "=================================="
            );


            throw new BusinessException(
                    ErrorCode.WEATHER_API_ERROR
            );
        }
    }


    /*
     * ==================================================
     * 날짜별 대표 날씨 생성
     * ==================================================
     *
     * 기상청은 시간별 데이터를 많이 내려주지만
     * 우리는 12:00의
     *
     * SKY
     * PTY
     *
     * 두 값만 사용한다.
     */

    private Map<LocalDate, WeatherCondition>
    toDailyWeather(

            List<Item> items,

            LocalDate startDate,

            LocalDate endDate
    ) {

        Map<LocalDate, DailyCodes> codesByDate =
                new HashMap<>();


        for (Item item : items) {


            /*
             * 12:00 데이터가 아니면 무시
             */
            if (
                    !REPRESENTATIVE_TIME
                            .equals(
                                    item.fcstTime()
                            )
            ) {

                continue;
            }


            /*
             * SKY / PTY 외에는 전부 무시
             *
             * TMP
             * POP
             * REH
             * WSD
             * PCP
             * ...
             */
            if (
                    !"SKY".equals(
                            item.category()
                    )
                            &&
                            !"PTY".equals(
                                    item.category()
                            )
            ) {

                continue;
            }


            if (
                    item.fcstDate() == null
                            ||
                            item.fcstValue() == null
            ) {

                continue;
            }


            LocalDate date;


            try {

                date =
                        LocalDate.parse(
                                item.fcstDate(),
                                DATE_FORMATTER
                        );

            } catch (
                    RuntimeException e
            ) {

                continue;
            }


            /*
             * 실제 여행 날짜가 아니면 무시
             */
            if (
                    date.isBefore(startDate)
                            ||
                            date.isAfter(endDate)
            ) {

                continue;
            }


            DailyCodes codes =

                    codesByDate
                            .computeIfAbsent(
                                    date,
                                    ignored ->
                                            new DailyCodes()
                            );


            codes.apply(
                    item.category(),
                    item.fcstValue()
            );
        }


        Map<LocalDate, WeatherCondition> result =
                new HashMap<>();


        codesByDate.forEach(

                (date, codes) ->

                        result.put(
                                date,
                                codes.toCondition()
                        )
        );


        return result;
    }


    /*
     * ==================================================
     * 가장 최근 단기예보 발표시각 계산
     * ==================================================
     */

    private BaseDateTime resolveBaseDateTime() {

        /*
         * 발표 직후 데이터가 바로
         * 준비되지 않는 경우를 고려하여
         * 현재 시간보다 10분 이전을 기준으로 잡는다.
         */
        LocalDateTime availableTime =

                LocalDateTime
                        .now(KOREA_ZONE)
                        .minusMinutes(10);


        LocalDate date =
                availableTime.toLocalDate();

        LocalTime time =
                availableTime.toLocalTime();


        /*
         * 뒤에서부터 검사
         *
         * 예)
         *
         * 현재 16:45
         * ↓
         * availableTime 16:35
         * ↓
         * 14:00 선택
         */
        for (
                int i = BASE_TIMES.size() - 1;
                i >= 0;
                i--
        ) {

            LocalTime baseTime =
                    BASE_TIMES.get(i);


            if (
                    !time.isBefore(baseTime)
            ) {

                return new BaseDateTime(
                        date,
                        baseTime
                );
            }
        }


        /*
         * 새벽이라 오늘 02시 자료도
         * 사용할 수 없으면
         *
         * 어제 23시 자료 사용
         */
        return new BaseDateTime(

                date.minusDays(1),

                LocalTime.of(
                        23,
                        0
                )
        );
    }


    /*
     * ==================================================
     * 위경도 → 기상청 격자좌표
     * ==================================================
     */

    private GridPoint convertToGrid(

            double latitude,

            double longitude
    ) {

        /*
         * 기상청 공식 DFS 격자 변환식
         */

        double re =
                6371.00877 / 5.0;

        double degToRad =
                Math.PI / 180.0;


        double slat1 =
                30.0 * degToRad;

        double slat2 =
                60.0 * degToRad;

        double olon =
                126.0 * degToRad;

        double olat =
                38.0 * degToRad;


        double xo =
                43.0;

        double yo =
                136.0;


        double sn =

                Math.tan(
                        Math.PI * 0.25
                                +
                                slat2 * 0.5
                )

                        /

                        Math.tan(
                                Math.PI * 0.25
                                        +
                                        slat1 * 0.5
                        );


        sn =
                Math.log(
                        Math.cos(slat1)
                                /
                                Math.cos(slat2)
                )

                        /

                        Math.log(sn);


        double sf =

                Math.tan(
                        Math.PI * 0.25
                                +
                                slat1 * 0.5
                );


        sf =
                Math.pow(
                        sf,
                        sn
                )

                        *

                        Math.cos(slat1)

                        /

                        sn;


        double ro =

                Math.tan(
                        Math.PI * 0.25
                                +
                                olat * 0.5
                );


        ro =
                re
                        *
                        sf
                        /
                        Math.pow(
                                ro,
                                sn
                        );


        double ra =

                Math.tan(

                        Math.PI * 0.25
                                +
                                latitude
                                        *
                                        degToRad
                                        *
                                        0.5
                );


        ra =
                re
                        *
                        sf
                        /
                        Math.pow(
                                ra,
                                sn
                        );


        double theta =

                longitude
                        *
                        degToRad
                        -
                        olon;


        if (
                theta > Math.PI
        ) {

            theta -=
                    2.0 * Math.PI;
        }


        if (
                theta < -Math.PI
        ) {

            theta +=
                    2.0 * Math.PI;
        }


        theta *= sn;


        int nx =

                (int) Math.floor(

                        ra
                                *
                                Math.sin(theta)

                                +
                                xo

                                +
                                0.5
                );


        int ny =

                (int) Math.floor(

                        ro

                                -

                                ra
                                        *
                                        Math.cos(theta)

                                +
                                yo

                                +
                                0.5
                );


        return new GridPoint(
                nx,
                ny
        );
    }


    /*
     * ==================================================
     * API Key 검증
     * ==================================================
     */

    private void validateServiceKey() {

        if (
                serviceKey == null
                        ||
                        serviceKey.isBlank()
        ) {

            throw new BusinessException(
                    ErrorCode
                            .WEATHER_API_KEY_NOT_CONFIGURED
            );
        }
    }


    /*
     * ==================================================
     * 기상청 응답 검증
     * ==================================================
     */

    private void validateResponse(
            KmaShortResponse response
    ) {

        if (
                response == null
                        ||
                        response.response() == null
                        ||
                        response.response()
                                .header() == null
        ) {

            throw new BusinessException(
                    ErrorCode.WEATHER_API_ERROR
            );
        }


        String resultCode =

                response
                        .response()
                        .header()
                        .resultCode();


        if (
                !"00".equals(resultCode)
                        &&
                        !"0000".equals(resultCode)
        ) {

            System.out.println(
                    "===== KMA SHORT RESULT ERROR ====="
            );

            System.out.println(
                    "resultCode = "
                            + resultCode
            );

            System.out.println(
                    "resultMsg = "
                            + response
                            .response()
                            .header()
                            .resultMsg()
            );

            System.out.println(
                    "=================================="
            );


            throw new BusinessException(
                    ErrorCode.WEATHER_API_ERROR
            );
        }


        if (
                response.response()
                        .body() == null
                        ||
                        response.response()
                                .body()
                                .items() == null
        ) {

            throw new BusinessException(
                    ErrorCode.WEATHER_API_ERROR
            );
        }
    }


    /*
     * ==================================================
     * SKY / PTY → WeatherCondition
     * ==================================================
     */

    private static class DailyCodes {

        private Integer sky;

        private Integer pty;


        private void apply(

                String category,

                String value
        ) {

            try {

                int code =
                        Integer.parseInt(
                                value
                        );


                if (
                        "SKY".equals(category)
                ) {

                    sky = code;
                }


                if (
                        "PTY".equals(category)
                ) {

                    pty = code;
                }


            } catch (
                    NumberFormatException ignored
            ) {

                /*
                 * 외부 데이터가 이상하면
                 * 그냥 UNKNOWN으로 남긴다.
                 */
            }
        }


        private WeatherCondition toCondition() {

            /*
             * PTY가 있으면
             * SKY보다 우선.
             *
             * 0 = 없음
             * 1 = 비
             * 2 = 비/눈
             * 3 = 눈
             * 4 = 소나기
             *
             * 일부 응답의
             * 5, 6, 7도 함께 처리.
             */

            if (
                    pty != null
                            &&
                            pty != 0
            ) {

                return switch (pty) {

                    case 2, 3, 6, 7 ->
                            WeatherCondition.SNOW;

                    case 1, 4, 5 ->
                            WeatherCondition.RAIN;

                    default ->
                            WeatherCondition.UNKNOWN;
                };
            }


            /*
             * PTY = 0이면 SKY 확인
             *
             * 1 = 맑음
             * 3 = 구름많음
             * 4 = 흐림
             */

            if (sky == null) {

                return WeatherCondition.UNKNOWN;
            }


            return switch (sky) {

                case 1 ->
                        WeatherCondition.SUNNY;

                case 3, 4 ->
                        WeatherCondition.CLOUDY;

                default ->
                        WeatherCondition.UNKNOWN;
            };
        }
    }


    /*
     * ==================================================
     * 내부 객체
     * ==================================================
     */

    private record GridPoint(
            int x,
            int y
    ) {
    }


    private record BaseDateTime(
            LocalDate date,
            LocalTime time
    ) {
    }


    /*
     * ==================================================
     * 기상청 API 응답 DTO
     * ==================================================
     */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KmaShortResponse(
            Response response
    ) {
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(
            Header header,
            Body body
    ) {
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(
            String resultCode,
            String resultMsg
    ) {
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(
            Items items
    ) {
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(
            List<Item> item
    ) {
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(

            String category,

            String fcstDate,

            String fcstTime,

            String fcstValue
    ) {
    }
}