package com.travel.external.flight;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.travel.flight.dto.FlightCandidate;
import com.travel.flight.type.FlightDirection;
import com.travel.flight.FlightPriceEstimator;
import com.travel.flight.type.FlightPriceType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;


@Component
public class AeroDataBoxFlightClient {

    private static final DateTimeFormatter REQUEST_FORMATTER =
            DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd'T'HH:mm"
            );
    /*
     * AeroDataBox 연속 호출 제한
     *
     * 최초 항공 검색 시
     * 가는 편 2번 + 오는 편 2번의
     * 외부 API 요청이 발생하므로
     * 요청 사이에 간격을 둔다.
     */
    private static final long MIN_REQUEST_INTERVAL_MS =
            1200L;


    private long lastRequestTime =
            0L;


    private final RestClient restClient;

    private final String apiKey;

    private final String apiHost;

    private final FlightPriceEstimator priceEstimator;


    private synchronized void waitForRateLimit() {

        while (true) {

            long now =
                    System.currentTimeMillis();


            long elapsed =
                    now
                            - lastRequestTime;


            long waitTime =
                    MIN_REQUEST_INTERVAL_MS
                            - elapsed;


            if (
                    waitTime <= 0
            ) {

                lastRequestTime =
                        now;

                return;
            }


            try {

                wait(
                        waitTime
                );

            } catch (
                    InterruptedException e
            ) {

                Thread.currentThread()
                        .interrupt();


                throw new IllegalStateException(
                        "AeroDataBox 요청 대기 중 중단되었습니다.",
                        e
                );
            }
        }
    }



    public AeroDataBoxFlightClient(

            @Value("${external.aerodatabox.base-url}")
            String baseUrl,

            @Value("${external.aerodatabox.api-key:}")
            String apiKey,

            @Value("${external.aerodatabox.api-host}")
            String apiHost,

            FlightPriceEstimator priceEstimator
    ) {

        /*
         * RestClient가 Apache HttpClient를 자동 선택하면
         * 429 응답에 대해 HttpRequestRetryExec가
         * 같은 요청을 자동 재실행할 수 있다.
         *
         * AeroDataBox / RapidAPI에서는
         * 429 발생 시 추가 호출 자체가 호출량을 더 소비할 수 있으므로,
         * JDK HttpClient를 명시적으로 사용해서
         * Apache 자동 재시도 경로를 사용하지 않도록 한다.
         */
        HttpClient httpClient =
                HttpClient
                        .newBuilder()
                        .build();


        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(
                        httpClient
                );


        this.restClient =
                RestClient
                        .builder()
                        .baseUrl(baseUrl)
                        .requestFactory(
                                requestFactory
                        )
                        .build();


        this.apiKey =
                apiKey;


        this.apiHost =
                apiHost;


        this.priceEstimator =
                priceEstimator;
    }


    @Cacheable(
            value = "flightSchedule",
            key =
                    "#departureAirport + ':' + "
                            + "#arrivalAirport + ':' + "
                            + "#from + ':' + "
                            + "#to + ':' + "
                            + "#peopleCount + ':' + "
                            + "#direction",
            unless = "#result == null"
    )
    public List<FlightCandidate> search(

            String departureAirport,

            String arrivalAirport,

            LocalDateTime from,

            LocalDateTime to,

            int peopleCount,

            FlightDirection direction
    ) {

        validateApiKey();


        String fromLocal =
                from.format(
                        REQUEST_FORMATTER
                );


        String toLocal =
                to.format(
                        REQUEST_FORMATTER
                );


        try {

            /*
             * AeroDataBox / RapidAPI 연속 호출 방지
             */
            waitForRateLimit();


            AeroDataBoxResponse response =

                    restClient
                            .get()

                            .uri(
                                    uriBuilder ->
                                            uriBuilder

                                                    .path(
                                                            "/flights/airports/iata/{airport}/{fromLocal}/{toLocal}"
                                                    )

                                                    .queryParam(
                                                            "withLeg",
                                                            true
                                                    )

                                                    .queryParam(
                                                            "direction",
                                                            "Departure"
                                                    )

                                                    .queryParam(
                                                            "withCancelled",
                                                            false
                                                    )

                                                    .queryParam(
                                                            "withCodeshared",
                                                            false
                                                    )

                                                    .queryParam(
                                                            "withCargo",
                                                            false
                                                    )

                                                    .queryParam(
                                                            "withPrivate",
                                                            false
                                                    )

                                                    .queryParam(
                                                            "withLocation",
                                                            false
                                                    )

                                                    .build(
                                                            departureAirport,
                                                            fromLocal,
                                                            toLocal
                                                    )
                            )

                            .header(
                                    "X-RapidAPI-Key",
                                    apiKey
                            )

                            .header(
                                    "X-RapidAPI-Host",
                                    apiHost
                            )

                            .retrieve()

                            .body(
                                    AeroDataBoxResponse.class
                            );


            if (
                    response == null
                            || response.departures() == null
            ) {

                return new ArrayList<>();
            }


            /*
             * AeroDataBox는
             * 출발공항의 전체 출발 항공편을 반환한다.
             *
             * 여기에서
             *
             * 1. 화물기 제외
             * 2. 국내선만
             * 3. 원하는 도착공항만
             *
             * 필터링한다.
             */
            List<FlightCandidate> candidates =

                    response
                            .departures()
                            .stream()

                            /*
                             * 화물기 제외
                             */
                            .filter(
                                    flight ->
                                            !Boolean.TRUE.equals(
                                                    flight.isCargo()
                                            )
                            )

                            /*
                             * 도착공항 데이터 존재 여부
                             */
                            .filter(
                                    flight ->
                                            flight.arrival() != null
                                                    && flight
                                                    .arrival()
                                                    .airport() != null
                            )

                            /*
                             * 국내선만
                             */
                            .filter(
                                    flight ->
                                            "kr".equalsIgnoreCase(
                                                    flight
                                                            .arrival()
                                                            .airport()
                                                            .countryCode()
                                            )
                            )

                            /*
                             * 원하는 목적공항만
                             */
                            .filter(
                                    flight ->
                                            arrivalAirport
                                                    .equalsIgnoreCase(
                                                            flight
                                                                    .arrival()
                                                                    .airport()
                                                                    .iata()
                                                    )
                            )

                            /*
                             * FlightCandidate DTO 변환
                             */
                            .map(
                                    flight ->
                                            convert(

                                                    flight,

                                                    departureAirport,

                                                    arrivalAirport,

                                                    peopleCount,

                                                    direction
                                            )
                            )

                            .filter(
                                    Objects::nonNull
                            )

                            /*
                             * 빠른 출발순
                             */
                            .sorted(
                                    Comparator.comparing(
                                            FlightCandidate
                                                    ::departureTime
                                    )
                            )

                            .toList();


            /*
             * Redis JDK 직렬화에서도
             * 명확한 ArrayList 형태로 저장
             */
            return new ArrayList<>(
                    candidates
            );


        } catch (
                RestClientResponseException e
        ) {

            int status =
                    e.getStatusCode()
                            .value();


            /*
             * 해당 날짜 / 시간에 항공편 없음
             */
            if (
                    status == 404
            ) {

                return new ArrayList<>();
            }


            /*
             * RapidAPI 인증 실패
             */
            if (
                    status == 401
                            || status == 403
            ) {

                throw new IllegalStateException(
                        "AeroDataBox 인증 또는 RapidAPI 구독 정보를 확인해주세요.",
                        e
                );
            }


            /*
             * 호출 제한
             *
             * 여기서는 재시도하지 않는다.
             *
             * 429가 순간 호출 제한인지,
             * 구독 요청량 소진인지 확인할 수 있도록
             * RapidAPI 관련 응답 헤더를 예외 메시지에 남긴다.
             */
            if (
                    status == 429
            ) {

                HttpHeaders headers =
                        e.getResponseHeaders();


                String retryAfter =
                        firstHeader(
                                headers,
                                HttpHeaders.RETRY_AFTER
                        );


                String rateRemaining =
                        firstHeader(
                                headers,
                                "x-ratelimit-remaining"
                        );


                String rateReset =
                        firstHeader(
                                headers,
                                "x-ratelimit-reset"
                        );


                String requestRemaining =
                        firstHeader(
                                headers,
                                "x-ratelimit-requests-remaining"
                        );


                String requestReset =
                        firstHeader(
                                headers,
                                "x-ratelimit-requests-reset"
                        );


                StringBuilder message =
                        new StringBuilder(
                                "AeroDataBox API 호출 한도를 초과했습니다."
                        );


                appendHeaderValue(
                        message,
                        "Retry-After",
                        retryAfter
                );


                appendHeaderValue(
                        message,
                        "RateRemaining",
                        rateRemaining
                );


                appendHeaderValue(
                        message,
                        "RateReset",
                        rateReset
                );


                appendHeaderValue(
                        message,
                        "RequestRemaining",
                        requestRemaining
                );


                appendHeaderValue(
                        message,
                        "RequestReset",
                        requestReset
                );


                throw new IllegalStateException(
                        message.toString(),
                        e
                );
            }


            throw new IllegalStateException(
                    "AeroDataBox API 호출에 실패했습니다. HTTP "
                            + status,
                    e
            );


        } catch (
                RestClientException e
        ) {

            throw new IllegalStateException(
                    "AeroDataBox API 연결에 실패했습니다.",
                    e
            );
        }
    }




    private FlightCandidate convert(

            Flight flight,

            String departureAirport,

            String arrivalAirport,

            int peopleCount,

            FlightDirection direction
    ) {

        /*
         * 출발/도착시간이 없으면
         * 화면에서 사용할 수 없으므로 제외
         */
        if (
                flight.departure() == null
                        || flight
                        .departure()
                        .scheduledTime() == null

                        || flight.arrival() == null
                        || flight
                        .arrival()
                        .scheduledTime() == null
        ) {

            return null;
        }


        LocalDateTime departureTime =
                parseLocalDateTime(

                        flight
                                .departure()
                                .scheduledTime()
                                .local()
                );


        LocalDateTime arrivalTime =
                parseLocalDateTime(

                        flight
                                .arrival()
                                .scheduledTime()
                                .local()
                );


        if (
                departureTime == null
                        || arrivalTime == null
        ) {

            return null;
        }


        String airlineCode =

                flight.airline() == null
                        ?
                        null
                        :
                        flight
                                .airline()
                                .iata();


        String airlineName =

                flight.airline() == null
                        ?
                        "Unknown"
                        :
                        flight
                                .airline()
                                .name();


        String flightNumber =
                normalizeFlightNumber(
                        flight.number()
                );


        /*
         * 예상 1인 운임
         */
        int estimatedPricePerPerson =

                priceEstimator
                        .estimatePricePerPerson(

                                departureAirport,

                                arrivalAirport,

                                airlineCode,

                                departureTime,

                                flightNumber
                        );


        /*
         * 전체 인원 예상가격
         */
        int estimatedTotalPrice =

                estimatedPricePerPerson
                        * peopleCount;


        String id =

                departureAirport
                        + "-"
                        + arrivalAirport
                        + "-"
                        + flightNumber
                        + "-"
                        + departureTime;


        return new FlightCandidate(

                id,

                direction,

                airlineName,

                airlineCode,

                flightNumber,

                departureAirport,

                arrivalAirport,

                departureTime,

                arrivalTime,

                estimatedPricePerPerson,

                estimatedTotalPrice,

                FlightPriceType.ESTIMATED,

                flight.aircraft() == null
                        ?
                        null
                        :
                        flight
                                .aircraft()
                                .model(),

                flight.status()
        );
    }


    /*
     * AeroDataBox:
     *
     * 2027-01-04 06:25+09:00
     *
     * Java OffsetDateTime:
     *
     * 2027-01-04T06:25+09:00
     */
    private LocalDateTime parseLocalDateTime(
            String value
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {

            return null;
        }


        try {

            String normalized =
                    value.replace(
                            " ",
                            "T"
                    );


            return OffsetDateTime
                    .parse(
                            normalized
                    )
                    .toLocalDateTime();


        } catch (
                RuntimeException e
        ) {

            return null;
        }
    }


    private String normalizeFlightNumber(
            String value
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {

            return "UNKNOWN";
        }


        /*
         * AeroDataBox:
         *
         * "7C 101"
         *
         * 우리 응답:
         *
         * "7C101"
         */
        return value
                .replace(
                        " ",
                        ""
                )
                .trim();
    }


    private String firstHeader(
            HttpHeaders headers,
            String name
    ) {

        if (
                headers == null
                        || name == null
                        || name.isBlank()
        ) {

            return null;
        }


        return headers.getFirst(
                name
        );
    }


    private void appendHeaderValue(
            StringBuilder message,
            String name,
            String value
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {

            return;
        }


        message
                .append(
                        " "
                )
                .append(
                        name
                )
                .append(
                        "="
                )
                .append(
                        value
                );
    }


    private void validateApiKey() {

        if (
                apiKey == null
                        || apiKey.isBlank()
        ) {

            throw new IllegalStateException(
                    "AERODATABOX_API_KEY 환경변수가 설정되지 않았습니다."
            );
        }
    }


    /*
     * ========================================
     * AeroDataBox Response DTO
     * ========================================
     */

    @JsonIgnoreProperties(
            ignoreUnknown = true
    )
    public record AeroDataBoxResponse(

            List<Flight> departures

    ) {
    }


    @JsonIgnoreProperties(
            ignoreUnknown = true
    )
    public record Flight(

            FlightPoint departure,

            FlightPoint arrival,

            String number,

            String status,

            /*
             * 사용하지는 않지만
             * 원본 데이터에는 존재할 수 있음.
             *
             * 나중에 필요하면 사용 가능.
             */
            String codeshareStatus,

            Boolean isCargo,

            Aircraft aircraft,

            Airline airline

    ) {
    }


    @JsonIgnoreProperties(
            ignoreUnknown = true
    )
    public record FlightPoint(

            Airport airport,

            ScheduledTime scheduledTime,

            String terminal

    ) {
    }


    @JsonIgnoreProperties(
            ignoreUnknown = true
    )
    public record ScheduledTime(

            String utc,

            String local

    ) {
    }


    @JsonIgnoreProperties(
            ignoreUnknown = true
    )
    public record Airport(

            String icao,

            String iata,

            String name,

            String countryCode,

            String timeZone

    ) {
    }


    @JsonIgnoreProperties(
            ignoreUnknown = true
    )
    public record Aircraft(

            String model

    ) {
    }


    @JsonIgnoreProperties(
            ignoreUnknown = true
    )
    public record Airline(

            String name,

            String iata,

            String icao

    ) {
    }
}
