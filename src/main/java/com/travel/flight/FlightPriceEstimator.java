package com.travel.flight;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

@Component
public class FlightPriceEstimator {

    private static final Set<String> FULL_SERVICE_CARRIERS =
            Set.of(
                    "KE",
                    "OZ"
            );


    public int estimatePricePerPerson(

            String departureAirport,

            String arrivalAirport,

            String airlineCode,

            LocalDate date
    ) {

        int basePrice =
                resolveBasePrice(
                        departureAirport,
                        arrivalAirport
                );


        double multiplier =
                1.0;


        /*
         * 대한항공 / 아시아나
         */
        if (
                airlineCode != null
                        && FULL_SERVICE_CARRIERS.contains(
                        airlineCode
                )
        ) {

            multiplier *= 1.20;
        }


        /*
         * 여름 성수기
         */
        if (
                date.getMonthValue() == 7
                        || date.getMonthValue() == 8
        ) {

            multiplier *= 1.25;
        }


        /*
         * 금/토/일
         */
        DayOfWeek dayOfWeek =
                date.getDayOfWeek();


        if (
                dayOfWeek == DayOfWeek.FRIDAY
                        || dayOfWeek == DayOfWeek.SATURDAY
                        || dayOfWeek == DayOfWeek.SUNDAY
        ) {

            multiplier *= 1.15;
        }


        int estimatedPrice =
                (int) (
                        basePrice
                                * multiplier
                );


        return roundToThousand(
                estimatedPrice
        );
    }


    private int resolveBasePrice(

            String departureAirport,

            String arrivalAirport
    ) {

        String route =
                departureAirport
                        + "-"
                        + arrivalAirport;


        return switch (route) {

            case "GMP-CJU",
                 "CJU-GMP" ->
                    70000;


            case "PUS-CJU",
                 "CJU-PUS" ->
                    55000;


            case "TAE-CJU",
                 "CJU-TAE" ->
                    55000;


            case "CJJ-CJU",
                 "CJU-CJJ" ->
                    60000;


            case "KWJ-CJU",
                 "CJU-KWJ" ->
                    50000;


            case "RSU-CJU",
                 "CJU-RSU" ->
                    50000;


            case "USN-CJU",
                 "CJU-USN" ->
                    55000;


            case "GMP-PUS",
                 "PUS-GMP" ->
                    65000;


            case "GMP-RSU",
                 "RSU-GMP" ->
                    60000;


            case "GMP-USN",
                 "USN-GMP" ->
                    60000;


            default ->
                    70000;
        };
    }


    private int roundToThousand(
            int value
    ) {

        return Math.round(
                value / 1000.0f
        ) * 1000;
    }
}