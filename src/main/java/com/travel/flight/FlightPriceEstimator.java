package com.travel.flight;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class FlightPriceEstimator {

    private static final Set<String> FULL_SERVICE_CARRIERS =
            Set.of("KE", "OZ");

    private static final Map<String, Double> AIRLINE_MULTIPLIERS =
            Map.ofEntries(
                    Map.entry("7C", 1.00),
                    Map.entry("TW", 0.96),
                    Map.entry("ZE", 0.94),
                    Map.entry("RS", 0.97),
                    Map.entry("RF", 0.95),
                    Map.entry("LJ", 1.02),
                    Map.entry("BX", 1.03)
            );

    public int estimatePricePerPerson(
            String departureAirport,
            String arrivalAirport,
            String airlineCode,
            LocalDateTime departureTime,
            String flightNumber
    ) {
        LocalDate date = departureTime.toLocalDate();
        int basePrice = resolveBasePrice(departureAirport, arrivalAirport);
        double multiplier = 1.0;

        if (airlineCode != null && FULL_SERVICE_CARRIERS.contains(airlineCode)) {
            multiplier *= 1.15;
        } else if (airlineCode != null) {
            multiplier *= AIRLINE_MULTIPLIERS.getOrDefault(airlineCode, 1.0);
        }

        if (date.getMonthValue() == 7 || date.getMonthValue() == 8) {
            multiplier *= 1.15;
        }

        DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (
                DayOfWeek.FRIDAY.equals(dayOfWeek)
                        || DayOfWeek.SATURDAY.equals(dayOfWeek)
                        || DayOfWeek.SUNDAY.equals(dayOfWeek)
        ) {
            multiplier *= 1.16;
        }

        int departureHour = departureTime.getHour();
        if ("CJU".equalsIgnoreCase(arrivalAirport) && departureHour < 12) {
            multiplier *= 1.06;
        }
        if ("CJU".equalsIgnoreCase(departureAirport) && departureHour >= 12) {
            multiplier *= 1.06;
        }

        int demandBasisPoints =
                Math.floorMod(
                        Objects.hash(
                                departureAirport,
                                arrivalAirport,
                                airlineCode,
                                flightNumber,
                                departureTime
                        ),
                        901
                ) - 450;
        multiplier *= 1.0 + demandBasisPoints / 10000.0;

        return roundToTen((int) (basePrice * multiplier));
    }

    private int resolveBasePrice(String departureAirport, String arrivalAirport) {
        String route = departureAirport + "-" + arrivalAirport;

        return switch (route) {
            case "GMP-CJU", "CJU-GMP" -> 94900;
            case "PUS-CJU", "CJU-PUS" -> 89900;
            case "TAE-CJU", "CJU-TAE" -> 91900;
            case "CJJ-CJU", "CJU-CJJ" -> 89900;
            case "KWJ-CJU", "CJU-KWJ" -> 75400;
            case "RSU-CJU", "CJU-RSU" -> 82900;
            case "USN-CJU", "CJU-USN" -> 87900;
            case "GMP-PUS", "PUS-GMP" -> 90900;
            case "GMP-RSU", "RSU-GMP" -> 84900;
            case "GMP-USN", "USN-GMP" -> 86900;
            default -> 94900;
        };
    }

    private int roundToTen(int value) {
        return Math.round(value / 10.0f) * 10;
    }
}
