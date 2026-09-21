package com.travel.global.time;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleTimeTest {
    @Test
    void calculatesMinutesInKoreaTimeZone() {
        var start = LocalDateTime.of(2026, 9, 20, 18, 30);
        assertThat(ScheduleTime.minutesBetween(start, start.plusMinutes(95))).isEqualTo(95);
    }
}
