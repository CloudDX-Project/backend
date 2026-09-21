package com.travel.global.time;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 여행 일정 시간 계산을 한국 표준시로 일관되게 처리한다.
 */
public final class ScheduleTime {
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private ScheduleTime() {
    }

    public static long minutesBetween(LocalDateTime start, LocalDateTime end) {
        return Duration.between(
                start.atZone(KOREA_ZONE),
                end.atZone(KOREA_ZONE)
        ).toMinutes();
    }
}
