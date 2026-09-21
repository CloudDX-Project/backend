package com.travel.trip.plan.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TripPlanCandidateDestinationCompatibilityTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "동문시장",
            "함덕해수욕장",
            "성산일출봉",
            "한담해안산책로",
            "애월 카페 거리",
            "곽지해수욕장",
            "협재해수욕장",
            "금능해변",
            "새별오름",
            "오설록 티 뮤지엄",
            "카멜리아힐",
            "산방산·용머리 해안",
            "천제연폭포",
            "중문색달해수욕장",
            "주상절리대"
    })
    void acceptsAllFifteenJejuRepresentativeDestinations(String name) {
        assertThat(TripPlanCandidateService.samePlaceName(name, name))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "애월카페거리",
            "애월 카페거리",
            "제주특별자치도 애월 카페 거리"
    })
    void matchesAewolCafeStreetDespiteSpacingOrRegionPrefix(String variant) {
        assertThat(TripPlanCandidateService.samePlaceName(
                "애월 카페 거리",
                variant
        )).isTrue();
    }
}
