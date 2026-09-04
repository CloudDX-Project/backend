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
         * 가는 편
         *
         * 여행 시작일의
         *
         * startTime
         * ~
         * 23:59
         *
         * 모든 항공편 조회
         * ========================================
         */

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


        /*
         * 한 번 더 시작시간 필터링.
         */
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


        /*
         * ========================================
         * 오는 편
         *
         * 종료일의
         *
         * 00:00
         * ~
         * 23:59
         *
         * 전체 조회
         *
         * 이후 실제 arrivalTime이
         * 사용자 endTime 이전인 편만 남긴다.
         * ========================================
         */

        LocalDateTime returnFrom =
                request.endDate()
                        .atStartOfDay();


        /*
         * 당일치기라면
         *
         * 가는 편 출발 전의 오는 편은
         * 후보가 될 수 없다.
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


        /*
         * 사용자의 endTime을
         *
         * "오는 비행기 출발시간 제한"
         *
         * 이 아니라
         *
         * "최종적으로 여행을 끝내야 하는 시간"
         *
         * 으로 해석.
         *
         * 즉 서울로 돌아오는 경우
         *
         * GMP 도착시간 <= endTime
         */

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


        /*
         * 당일치기라면
         * 가는 편 도착보다 반드시
         * 이후에 출발하는 오는 편만 보여야 하지만,
         *
         * 사용자가 어느 가는 편을 선택할지
         * 아직 모르므로 여기서는 전체 return 후보를
         * 내려준다.
         *
         * 프론트에서 outbound 선택 후
         * 필요한 경우 다시 필터 가능.
         */


        return new FlightSearchResponse(

                departureAirport,

                arrivalAirport,

                outboundFlights,

                returnFlights
        );
    }


    /*
     * ========================================
     * AeroDataBox 조회 범위 분할
     *
     * 12시간 단위로 나눠 호출하고 합친다.
     *
     * 예:
     *
     * 06:00 ~ 18:00
     * 18:00 ~ 23:59
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


            /*
             * 정확히 경계를 이어서 호출.
             *
             * 중복은 마지막에 제거.
             */
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

        /*
         * 시작일 > 종료일 불가
         */
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


        /*
         * 당일 여행
         *
         * startTime < endTime
         */
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