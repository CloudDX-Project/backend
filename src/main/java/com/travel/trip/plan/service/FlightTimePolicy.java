package com.travel.trip.plan.service;

import java.time.LocalDateTime;

/** 국내선 일정용 기본 여유시간. 항공사 규정이나 실제 대기시간을 뜻하지 않는다. */
public final class FlightTimePolicy {
    public static final int AIRPORT_BUFFER_MINUTES = 90;
    public static final int ARRIVAL_PROCESSING_MINUTES = 30;
    public static final int RENTAL_PICKUP_MINUTES = 20;
    public static final int RENTAL_RETURN_MINUTES = 20;

    private FlightTimePolicy() {}

    public static LocalDateTime airportDeadline(LocalDateTime departure) {
        return departure.minusMinutes(AIRPORT_BUFFER_MINUTES);
    }

    public static boolean canReachAirport(LocalDateTime activityEnd, long transferMinutes,
                                         LocalDateTime flightDeparture) {
        return !activityEnd.plusMinutes(transferMinutes).isAfter(airportDeadline(flightDeparture));
    }
}
