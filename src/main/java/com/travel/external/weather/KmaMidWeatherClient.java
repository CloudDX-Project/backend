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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class KmaMidWeatherClient {

    private static final ZoneId KOREA_ZONE =
            ZoneId.of("Asia/Seoul");

    private static final DateTimeFormatter
            TM_FC_FORMATTER =
            DateTimeFormatter.ofPattern(
                    "yyyyMMddHHmm"
            );


    /*
     * 강원도는 영동 / 영서가
     * 별도 예보구역이다.
     */
    private static final Set<String>
            GANGWON_YEONGDONG =
            Set.of(
                    "강릉시",
                    "동해시",
                    "태백시",
                    "속초시",
                    "삼척시",
                    "고성군",
                    "양양군"
            );


    private static final Set<String>
            GANGWON_YEONGSEO =
            Set.of(
                    "춘천시",
                    "원주시",
                    "홍천군",
                    "횡성군",
                    "영월군",
                    "평창군",
                    "정선군",
                    "철원군",
                    "화천군",
                    "양구군",
                    "인제군"
            );


    private final RestClient restClient;
    private final String serviceKey;


    public KmaMidWeatherClient(

            @Value("${weather.kma.mid.base-url}")
            String baseUrl,

            @Value("${weather.kma.mid.service-key:}")
            String serviceKey
    ) {

        this.restClient =
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .build();

        this.serviceKey =
                serviceKey;
    }


    public Map<LocalDate, WeatherCondition>
    getDailyWeather(

            String destination,

            LocalDate startDate,

            LocalDate endDate
    ) {

        LocalDate today =
                LocalDate.now(
                        KOREA_ZONE
                );


        /*
         * 중기예보 범위와
         * 여행기간이 겹치지 않으면
         * API 호출하지 않음
         */
        if (
                endDate.isBefore(
                        today.plusDays(3)
                )
                        ||
                        startDate.isAfter(
                                today.plusDays(10)
                        )
        ) {

            return Map.of();
        }


        String regionId =
                resolveRegionId(
                        destination
                );


        /*
         * 중기예보 지역을
         * 특정할 수 없는 경우
         */
        if (regionId == null) {

            return Map.of();
        }


        validateServiceKey();


        BaseDateTime base =
                resolveBaseDateTime();


        String tmFc =

                LocalDateTime
                        .of(
                                base.date(),
                                base.time()
                        )
                        .format(
                                TM_FC_FORMATTER
                        );


        try {

            KmaMidResponse response =

                    restClient
                            .get()

                            .uri(uriBuilder ->
                                    uriBuilder

                                            .path(
                                                    "/getMidLandFcst"
                                            )

                                            .queryParam(
                                                    "serviceKey",
                                                    serviceKey
                                            )

                                            .queryParam(
                                                    "pageNo",
                                                    1
                                            )

                                            .queryParam(
                                                    "numOfRows",
                                                    10
                                            )

                                            .queryParam(
                                                    "dataType",
                                                    "JSON"
                                            )

                                            .queryParam(
                                                    "regId",
                                                    regionId
                                            )

                                            .queryParam(
                                                    "tmFc",
                                                    tmFc
                                            )

                                            .build()
                            )

                            .retrieve()

                            .body(
                                    KmaMidResponse.class
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

                    base.date(),

                    items.get(0),

                    startDate,

                    endDate
            );


        } catch (
                RestClientResponseException e
        ) {

            System.out.println(
                    "===== KMA MID API ERROR ====="
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
                    "=============================="
            );


            throw new BusinessException(
                    ErrorCode.WEATHER_API_ERROR
            );


        } catch (
                RestClientException e
        ) {

            System.out.println(
                    "===== KMA MID CLIENT ERROR ====="
            );

            System.out.println(
                    e.getMessage()
            );

            System.out.println(
                    "================================"
            );


            throw new BusinessException(
                    ErrorCode.WEATHER_API_ERROR
            );
        }
    }


    /*
     * 중기예보 결과를 날짜별 대표날씨로 변환
     */
    private Map<LocalDate, WeatherCondition>
    toDailyWeather(

            LocalDate baseDate,

            Item item,

            LocalDate startDate,

            LocalDate endDate
    ) {

        Map<LocalDate, WeatherCondition> result =
                new HashMap<>();


        /*
         * D+3 ~ D+7
         * 오전 / 오후 데이터
         */

        putMorningAfternoon(
                result,
                baseDate.plusDays(3),
                item.wf3Am(),
                item.wf3Pm(),
                startDate,
                endDate
        );


        putMorningAfternoon(
                result,
                baseDate.plusDays(4),
                item.wf4Am(),
                item.wf4Pm(),
                startDate,
                endDate
        );


        putMorningAfternoon(
                result,
                baseDate.plusDays(5),
                item.wf5Am(),
                item.wf5Pm(),
                startDate,
                endDate
        );


        putMorningAfternoon(
                result,
                baseDate.plusDays(6),
                item.wf6Am(),
                item.wf6Pm(),
                startDate,
                endDate
        );


        putMorningAfternoon(
                result,
                baseDate.plusDays(7),
                item.wf7Am(),
                item.wf7Pm(),
                startDate,
                endDate
        );


        /*
         * D+8 ~ D+10
         * 하루 단위 데이터
         */

        putSingle(
                result,
                baseDate.plusDays(8),
                item.wf8(),
                startDate,
                endDate
        );


        putSingle(
                result,
                baseDate.plusDays(9),
                item.wf9(),
                startDate,
                endDate
        );


        putSingle(
                result,
                baseDate.plusDays(10),
                item.wf10(),
                startDate,
                endDate
        );


        return result;
    }


    private void putMorningAfternoon(

            Map<LocalDate, WeatherCondition> result,

            LocalDate date,

            String morning,

            String afternoon,

            LocalDate startDate,

            LocalDate endDate
    ) {

        if (
                date.isBefore(startDate)
                        ||
                        date.isAfter(endDate)
        ) {

            return;
        }


        WeatherCondition morningCondition =
                convertCondition(
                        morning
                );


        WeatherCondition afternoonCondition =
                convertCondition(
                        afternoon
                );


        result.put(

                date,

                representativeCondition(
                        morningCondition,
                        afternoonCondition
                )
        );
    }


    private void putSingle(

            Map<LocalDate, WeatherCondition> result,

            LocalDate date,

            String weather,

            LocalDate startDate,

            LocalDate endDate
    ) {

        if (
                date.isBefore(startDate)
                        ||
                        date.isAfter(endDate)
        ) {

            return;
        }


        result.put(

                date,

                convertCondition(
                        weather
                )
        );
    }


    /*
     * 오전 / 오후가 다를 경우
     *
     * 여행 일정에 더 큰 영향을 주는
     * 날씨를 대표값으로 사용
     */
    private WeatherCondition representativeCondition(

            WeatherCondition first,

            WeatherCondition second
    ) {

        if (
                first == WeatherCondition.SNOW
                        ||
                        second == WeatherCondition.SNOW
        ) {

            return WeatherCondition.SNOW;
        }


        if (
                first == WeatherCondition.RAIN
                        ||
                        second == WeatherCondition.RAIN
        ) {

            return WeatherCondition.RAIN;
        }


        if (
                first == WeatherCondition.CLOUDY
                        ||
                        second == WeatherCondition.CLOUDY
        ) {

            return WeatherCondition.CLOUDY;
        }


        if (
                first == WeatherCondition.SUNNY
                        ||
                        second == WeatherCondition.SUNNY
        ) {

            return WeatherCondition.SUNNY;
        }


        return WeatherCondition.UNKNOWN;
    }


    /*
     * 기상청 문자열 → WeatherCondition
     */
    private WeatherCondition convertCondition(
            String value
    ) {

        if (
                value == null
                        ||
                        value.isBlank()
        ) {

            return WeatherCondition.UNKNOWN;
        }


        if (
                value.contains("눈")
        ) {

            return WeatherCondition.SNOW;
        }


        if (
                value.contains("비")
                        ||
                        value.contains("소나기")
        ) {

            return WeatherCondition.RAIN;
        }


        if (
                value.contains("구름")
                        ||
                        value.contains("흐림")
        ) {

            return WeatherCondition.CLOUDY;
        }


        if (
                value.contains("맑")
        ) {

            return WeatherCondition.SUNNY;
        }


        return WeatherCondition.UNKNOWN;
    }


    /*
     * 목적지 → 기상청 중기예보 지역코드
     */
    private String resolveRegionId(
            String destination
    ) {

        if (
                destination == null
                        ||
                        destination.isBlank()
        ) {

            return null;
        }


        String value =
                destination.trim();


        /*
         * 서울 / 인천 / 경기
         */
        if (
                startsWithAny(
                        value,
                        "서울",
                        "인천",
                        "경기"
                )
        ) {

            return "11B00000";
        }


        /*
         * 대전 / 세종 / 충남
         */
        if (
                startsWithAny(
                        value,
                        "대전",
                        "세종",
                        "충청남도",
                        "충남"
                )
        ) {

            return "11C20000";
        }


        /*
         * 충북
         */
        if (
                startsWithAny(
                        value,
                        "충청북도",
                        "충북"
                )
        ) {

            return "11C10000";
        }


        /*
         * 전북
         */
        if (
                startsWithAny(
                        value,
                        "전북특별자치도",
                        "전라북도",
                        "전북"
                )
        ) {

            return "11F10000";
        }


        /*
         * 광주 / 전남
         */
        if (
                startsWithAny(
                        value,
                        "광주",
                        "전라남도",
                        "전남"
                )
        ) {

            return "11F20000";
        }


        /*
         * 대구 / 경북
         */
        if (
                startsWithAny(
                        value,
                        "대구",
                        "경상북도",
                        "경북"
                )
        ) {

            return "11H10000";
        }


        /*
         * 부산 / 울산 / 경남
         */
        if (
                startsWithAny(
                        value,
                        "부산",
                        "울산",
                        "경상남도",
                        "경남"
                )
        ) {

            return "11H20000";
        }


        /*
         * 제주
         */
        if (
                startsWithAny(
                        value,
                        "제주"
                )
        ) {

            return "11G00000";
        }


        /*
         * 강원
         */
        if (
                startsWithAny(
                        value,
                        "강원"
                )
        ) {

            for (
                    String area :
                    GANGWON_YEONGDONG
            ) {

                if (
                        value.contains(area)
                ) {

                    return "11D20000";
                }
            }


            for (
                    String area :
                    GANGWON_YEONGSEO
            ) {

                if (
                        value.contains(area)
                ) {

                    return "11D10000";
                }
            }
        }


        return null;
    }


    private boolean startsWithAny(

            String value,

            String... prefixes
    ) {

        for (
                String prefix :
                prefixes
        ) {

            if (
                    value.startsWith(prefix)
            ) {

                return true;
            }
        }


        return false;
    }


    /*
     * 최근 중기예보 발표 시각
     *
     * 06:00
     * 18:00
     */
    private BaseDateTime resolveBaseDateTime() {


        LocalDateTime availableTime =

                LocalDateTime
                        .now(KOREA_ZONE)
                        .minusMinutes(10);


        LocalDate date =
                availableTime.toLocalDate();

        LocalTime time =
                availableTime.toLocalTime();


        /*
         * 18시 이후
         */
        if (
                !time.isBefore(
                        LocalTime.of(
                                18,
                                0
                        )
                )
        ) {

            return new BaseDateTime(

                    date,

                    LocalTime.of(
                            18,
                            0
                    )
            );
        }


        /*
         * 06시 이후
         */
        if (
                !time.isBefore(
                        LocalTime.of(
                                6,
                                0
                        )
                )
        ) {

            return new BaseDateTime(

                    date,

                    LocalTime.of(
                            6,
                            0
                    )
            );
        }


        /*
         * 새벽이면 전날 18시
         */
        return new BaseDateTime(

                date.minusDays(1),

                LocalTime.of(
                        18,
                        0
                )
        );
    }


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


    private void validateResponse(
            KmaMidResponse response
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
                    "KMA MID resultCode = "
                            + resultCode
            );

            System.out.println(
                    "KMA MID resultMsg = "
                            + response
                            .response()
                            .header()
                            .resultMsg()
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


    private record BaseDateTime(
            LocalDate date,
            LocalTime time
    ) {
    }


    /*
     * ================================
     * 기상청 중기예보 DTO
     * ================================
     */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KmaMidResponse(
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

            String regId,

            String wf3Am,
            String wf3Pm,

            String wf4Am,
            String wf4Pm,

            String wf5Am,
            String wf5Pm,

            String wf6Am,
            String wf6Pm,

            String wf7Am,
            String wf7Pm,

            String wf8,

            String wf9,

            String wf10
    ) {
    }
}