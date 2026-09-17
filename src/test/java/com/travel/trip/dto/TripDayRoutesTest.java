package com.travel.trip.dto;

import com.travel.trip.entity.*;
import com.travel.routing.dto.RoutePoint;
import com.travel.routing.util.RoutePathCodec;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class TripDayRoutesTest {
    @Test void restoredTripKeepsTransportSegmentsAndRoadGeometry() {
        TripDay day = TripDay.builder().dayNumber(1).date(LocalDate.of(2026, 9, 18)).build();
        List<RoutePoint> path = List.of(new RoutePoint(33.5, 126.5), new RoutePoint(33.6, 126.6));
        day.addTransportSegment(TransportSegment.builder().tripDay(day).sequence(1)
                .mode(SegmentTransportMode.RENTAL_CAR).routeProvider("KAKAO_MOBILITY")
                .routePathJson(RoutePathCodec.encode(path)).build());
        TripDayResponse response = TripDayResponse.from(day);
        assertThat(response.transportSegments()).hasSize(1);
        assertThat(response.transportSegments().getFirst().path()).isEqualTo(path);
    }
}
