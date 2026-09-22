package com.travel.trip.plan.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TripPromptDayConstraintParserTest {

    @Test
    void parsesPlaceThenDayOnly() {
        assertThat(TripPromptDayConstraintParser.requestedDayFor(
                "한담해안산책로는 3일차때 가고싶어요",
                "한담해안산책로",
                3
        )).isEqualTo(3);
    }

    @Test
    void parsesDayThenPlace() {
        assertThat(TripPromptDayConstraintParser.requestedDayFor(
                "2일차에는 새별오름에 가고 싶어요",
                "새별오름",
                3
        )).isEqualTo(2);
    }

    @Test
    void ignoresTimeOnlyPrompt() {
        assertThat(TripPromptDayConstraintParser.requestedDayFor(
                "한담해안산책로는 저녁에 가고 싶어요",
                "한담해안산책로",
                3
        )).isNull();
    }
}
