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
    @Test
    void normalizesSpacesPunctuationAndKeepsLongestOverlappingName() {
        List<TripPromptDayConstraintParser.DayConstraint> result =
                TripPromptDayConstraintParser.parse(
                        "한담 해안 산책로!! 3일차",
                        3,
                        List.of(
                                new TripPromptDayConstraintParser.NamedAttraction(1L, "한담"),
                                new TripPromptDayConstraintParser.NamedAttraction(2L, "한담해안산책로")
                        )
                );

        assertThat(result)
                .extracting(TripPromptDayConstraintParser.DayConstraint::attractionId)
                .containsExactly(2L);
    }

    @Test
    void ignoresBlankPlaceNameAndShortCandidateName() {
        assertThat(TripPromptDayConstraintParser.requestedDayFor("한담 2일차", " ", 3)).isNull();

        assertThat(TripPromptDayConstraintParser.parse(
                "산 2일차",
                3,
                List.of(new TripPromptDayConstraintParser.NamedAttraction(1L, "산"))
        )).isEmpty();
    }

    @Test
    void firstMentionWinsForSameAttractionAcrossClauses() {
        List<TripPromptDayConstraintParser.DayConstraint> result =
                TripPromptDayConstraintParser.parse(
                        "새별오름은 2일차, 그리고 새별오름은 3일차",
                        3,
                        List.of(new TripPromptDayConstraintParser.NamedAttraction(10L, "새별오름"))
                );

        assertThat(result).singleElement()
                .extracting(TripPromptDayConstraintParser.DayConstraint::dayNumber)
                .isEqualTo(2);
    }

    @Test
    void ignoresNullCandidateEntriesAndDuplicateIds() {
        List<TripPromptDayConstraintParser.DayConstraint> result =
                TripPromptDayConstraintParser.parse(
                        "새별오름 2일차",
                        3,
                        java.util.Arrays.asList(
                                null,
                                new TripPromptDayConstraintParser.NamedAttraction(10L, "새별오름"),
                                new TripPromptDayConstraintParser.NamedAttraction(10L, "새별오름"),
                                new TripPromptDayConstraintParser.NamedAttraction(null, "새별오름"),
                                new TripPromptDayConstraintParser.NamedAttraction(20L, null)
                        )
                );

        assertThat(result).hasSize(1);
    }

}
