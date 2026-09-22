package com.travel.trip.plan.service;

import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void parsesMultipleAttractionsIntoRequestedDays() {
        List<TripPromptDayConstraintParser.DayConstraint> result =
                TripPromptDayConstraintParser.parse(
                        "새별오름은 2일차, 한담해안산책로는 3일차에 가고 싶어요",
                        3,
                        List.of(
                                new TripPromptDayConstraintParser.NamedAttraction(10L, "새별오름"),
                                new TripPromptDayConstraintParser.NamedAttraction(20L, "한담해안산책로")
                        )
                );

        assertThat(result)
                .extracting(
                        TripPromptDayConstraintParser.DayConstraint::attractionId,
                        TripPromptDayConstraintParser.DayConstraint::dayNumber
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(10L, 2),
                        org.assertj.core.groups.Tuple.tuple(20L, 3)
                );
    }

    @Test
    void ignoresUnknownOutOfRangeAndMissingInputs() {
        List<TripPromptDayConstraintParser.NamedAttraction> attractions = List.of(
                new TripPromptDayConstraintParser.NamedAttraction(10L, "새별오름")
        );

        assertThat(TripPromptDayConstraintParser.parse(null, 3, attractions)).isEmpty();
        assertThat(TripPromptDayConstraintParser.parse("새별오름은 4일차", 3, attractions)).isEmpty();
        assertThat(TripPromptDayConstraintParser.parse("성산일출봉은 2일차", 3, attractions)).isEmpty();
        assertThat(TripPromptDayConstraintParser.parse("새별오름은 2일차", 0, attractions)).isEmpty();
    }
}
