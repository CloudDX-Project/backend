package com.travel.flight;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class FlightPriceEstimatorTest {

    private final FlightPriceEstimator estimator = new FlightPriceEstimator();

    @Test
    void gimpoJejuWeekdayFareUsesPublishedMarketRangeAndTenWonUnits() {
        int price = estimator.estimatePricePerPerson(
                "GMP", "CJU", "7C",
                LocalDateTime.of(2026, 9, 16, 10, 10),
                "7C153"
        );

        assertThat(price).isBetween(95000, 106000);
        assertThat(price % 10).isZero();
    }

    @Test
    void weekendAndFullServiceFlightsCostMoreThanWeekdayLowCostFlight() {
        int weekdayLowCost = estimator.estimatePricePerPerson(
                "GMP", "CJU", "7C",
                LocalDateTime.of(2026, 9, 16, 10, 10),
                "7C153"
        );
        int weekendFullService = estimator.estimatePricePerPerson(
                "GMP", "CJU", "KE",
                LocalDateTime.of(2026, 9, 19, 10, 10),
                "KE1073"
        );

        assertThat(weekendFullService).isGreaterThan(weekdayLowCost);
    }

    @Test
    void differentFlightsReceiveStableButNonUniformEstimatedFares() {
        LocalDateTime departure = LocalDateTime.of(2026, 9, 16, 10, 10);

        int first = estimator.estimatePricePerPerson(
                "GMP", "CJU", "7C", departure, "7C153"
        );
        int same = estimator.estimatePricePerPerson(
                "GMP", "CJU", "7C", departure, "7C153"
        );
        int another = estimator.estimatePricePerPerson(
                "GMP", "CJU", "TW", departure.minusMinutes(25), "TW707"
        );

        assertThat(same).isEqualTo(first);
        assertThat(another).isNotEqualTo(first);
    }
}
