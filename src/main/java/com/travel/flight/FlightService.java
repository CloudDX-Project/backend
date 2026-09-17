package com.travel.flight;

import com.travel.external.flight.AeroDataBoxFlightClient;
import com.travel.flight.dto.FlightCandidate;
import com.travel.flight.dto.FlightSearchRequest;
import com.travel.flight.dto.FlightSearchResponse;
import com.travel.flight.type.FlightDirection;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Service
public class FlightService {

    private final AirportMapper airportMapper;

    private final AeroDataBoxFlightClient flightClient;


    public FlightService(

            AirportMapper airportMapper,

            AeroDataBoxFlightClient flightClient
    ) {

        this.airportMapper =
                airportMapper;

        this.flightClient =
                flightClient;
    }


    public FlightSearchResponse search(
            FlightSearchRequest request
    ) {

        validateRequest(
                request
        );


        /*
         * ========================================
         * 지역 → 대표공항
         *
         * 서울 → GMP
         * 제주 → CJU
         * ========================================
         */

        String departureAirport =
                airportMapper.resolve(
                        request.departure()
                );


        String arrivalAirport =
                airportMapper.resolve(
                        request.destination()
                );


        if (
                departureAirport.equalsIgnoreCase(
                        arrivalAirport
                )
        ) {

            throw new IllegalArgumentException(
                    "출발공항과 도착공항이 동일합니다."
            );
        }


        /*
         * ========================================
         * 한 요청에서는 한 방향만 조회한다.
         *
         * OUTBOUND 요청
         * → 가는 편만 AeroDataBox 호출
         *
         * RETURN 요청
         * → 오는 편만 AeroDataBox 호출
         *
         * 하루 전체를 12시간 단위로 나누므로
         * 각 요청당 외부 API는 최대 2회 호출된다.
         * ========================================
         */

        if (
                request.direction()
                        == FlightDirection.OUTBOUND
        ) {

            return searchOutbound(
                    request,
                    departureAirport,
                    arrivalAirport
            );
        }


        return searchReturn(
                request,
                departureAirport,
                arrivalAirport
        );
    }


    private FlightSearchResponse searchOutbound(

            FlightSearchRequest request,

            String departureAirport,

            String arrivalAirport
    ) {

        LocalDateTime outboundFrom =
                LocalDateTime.of(

                        request.startDate(),

                        request.startTime()
                );


        LocalDateTime outboundTo =
                request.startDate()
                        .atTime(
                                23,
                                59
                        );


        List<FlightCandidate> outboundFlights =
                searchFullRange(

                        departureAirport,

                        arrivalAirport,

                        outboundFrom,

                        outboundTo,

                        request.peopleCount(),

                        FlightDirection.OUTBOUND
                );


        outboundFlights =
                outboundFlights
                        .stream()

                        .filter(
                                flight ->
                                        !flight
                                                .departureTime()
                                                .isBefore(
                                                        outboundFrom
                                                )
                        )

                        .sorted(
                                Comparator.comparing(
                                        FlightCandidate
                                                ::departureTime
                                )
                        )

                        .toList();


        return new FlightSearchResponse(

                departureAirport,

                arrivalAirport,

                outboundFlights,

                List.of()
        );
    }


    private FlightSearchResponse searchReturn(

            FlightSearchRequest request,

            String departureAirport,

            String arrivalAirport
    ) {

        LocalDateTime returnFrom =
                request.endDate()
                        .atStartOfDay();


        /*
         * 당일치기라면 여행 시작 전 시간대는
         * 오는 편 후보가 될 수 없다.
         */
        if (
                request.startDate()
                        .equals(
                                request.endDate()
                        )
        ) {

            returnFrom =
                    LocalDateTime.of(

                            request.endDate(),

                            request.startTime()
                    );
        }


        LocalDateTime returnTo =
                request.endDate()
                        .atTime(
                                23,
                                59
                        );


        List<FlightCandidate> returnFlights =
                searchFullRange(

                        arrivalAirport,

                        departureAirport,

                        returnFrom,

                        returnTo,

                        request.peopleCount(),

                        FlightDirection.RETURN
                );


        LocalDateTime tripEnd =
                LocalDateTime.of(

                        request.endDate(),

                        request.endTime()
                );


        returnFlights =
                returnFlights
                        .stream()

                        .filter(
                                flight ->
                                        !flight
                                                .arrivalTime()
                                                .isAfter(
                                                        tripEnd
                                                )
                        )

                        .sorted(
                                Comparator.comparing(
                                        FlightCandidate
                                                ::departureTime
                                )
                        )

                        .toList();


        return new FlightSearchResponse(

                departureAirport,

                arrivalAirport,

                List.of(),

                returnFlights
        );
    }


    /*
     * ========================================
     * AeroDataBox 조회 범위 분할
     *
     * 12시간 단위로 나눠 호출하고 합친다.
     *
     * 하루 전체 조회라면 한 방향당 최대 2회.
     *
     * 경계시간이 중복될 수 있으므로
     * 마지막에 removeDuplicates().
     * ========================================
     */

    private List<FlightCandidate> searchFullRange(

            String departureAirport,

            String arrivalAirport,

            LocalDateTime from,

            LocalDateTime to,

            int peopleCount,

            FlightDirection direction
    ) {

        List<FlightCandidate> result =
                new ArrayList<>();


        if (
                !from.isBefore(to)
        ) {

            return result;
        }


        LocalDateTime cursor =
                from;


        while (
                cursor.isBefore(to)
        ) {

            LocalDateTime chunkTo =
                    cursor.plusHours(
                            12
                    );


            if (
                    chunkTo.isAfter(
                            to
                    )
            ) {

                chunkTo =
                        to;
            }


            List<FlightCandidate> chunk =
                    flightClient.search(

                            departureAirport,

                            arrivalAirport,

                            cursor,

                            chunkTo,

                            peopleCount,

                            direction
                    );


            result.addAll(
                    chunk
            );


            cursor =
                    chunkTo;
        }


        return removeDuplicates(
                result
        );
    }


    /*
     * ========================================
     * 중복 제거
     * ========================================
     */

    private List<FlightCandidate> removeDuplicates(

            List<FlightCandidate> flights
    ) {

        Map<String, FlightCandidate> unique =
                new LinkedHashMap<>();


        for (
                FlightCandidate flight
                :
                flights
        ) {

            String key =
                    flight.flightNumber()
                            + ":"
                            + flight.departureAirport()
                            + ":"
                            + flight.arrivalAirport()
                            + ":"
                            + flight.departureTime();


            unique.putIfAbsent(
                    key,
                    flight
            );
        }


        return new ArrayList<>(
                unique.values()
        );
    }


    /*
     * ========================================
     * 요청 검증
     * ========================================
     */

    private void validateRequest(
            FlightSearchRequest request
    ) {

        if (
                request.startDate()
                        .isAfter(
                                request.endDate()
                        )
        ) {

            throw new IllegalArgumentException(
                    "여행 시작일은 종료일보다 늦을 수 없습니다."
            );
        }


        if (
                request.startDate()
                        .equals(
                                request.endDate()
                        )
                        &&
                        !request.startTime()
                                .isBefore(
                                        request.endTime()
                                )
        ) {

            throw new IllegalArgumentException(
                    "당일 여행의 종료시간은 시작시간보다 늦어야 합니다."
            );
        }
    }
}
