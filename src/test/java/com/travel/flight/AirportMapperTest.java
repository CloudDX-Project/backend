package com.travel.flight;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AirportMapperTest {

    private final AirportMapper airportMapper = new AirportMapper();

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
    void resolvesRepresentativeJejuDestinationsToJejuAirport(String destination) {
        assertThat(airportMapper.resolve(destination)).isEqualTo("CJU");
    }
}
