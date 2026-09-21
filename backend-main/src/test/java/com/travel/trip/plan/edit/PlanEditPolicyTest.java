package com.travel.trip.plan.edit;

import com.travel.trip.entity.SegmentTransportMode;
import com.travel.trip.plan.dto.TripPlanDayResponse;
import com.travel.trip.plan.dto.TripPlanItemResponse;
import com.travel.trip.plan.type.TripPlanItemType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanEditPolicyTest {
    private final PlanEditPolicy policy = new PlanEditPolicy();

    @Test
    void editablePlacesCanBeReorderedWhileAnchorsStayFixed() {
        var original = List.of(day());
        var request = new PlanEditRequest(3L, "request-1", List.of(new PlanEditRequest.Day(1, List.of(
                item("1:1"), item("1:3"), item("1:2"), item("1:4")
        ))));

        var source = policy.validate(original, request);

        assertThat(source).containsKeys("1:1", "1:2", "1:3", "1:4");
    }

    @Test
    void protectedAnchorCannotBeEdited() {
        var request = new PlanEditRequest(3L, "request-1", List.of(new PlanEditRequest.Day(1, List.of(
                new PlanEditRequest.Item("1:1", 30, null, null),
                item("1:2"), item("1:3"), item("1:4")
        ))));

        assertThatThrownBy(() -> policy.validate(List.of(day()), request))
                .isInstanceOf(PlanEditException.class)
                .hasMessageContaining("직접 수정");
    }

    private static PlanEditRequest.Item item(String key) {
        return new PlanEditRequest.Item(key, null, null, null);
    }

    private static TripPlanDayResponse day() {
        return new TripPlanDayResponse(1, LocalDate.of(2026, 9, 19), List.of(
                planItem(1, TripPlanItemType.AIRPORT),
                planItem(2, TripPlanItemType.ATTRACTION),
                planItem(3, TripPlanItemType.CAFE),
                planItem(4, TripPlanItemType.ACCOMMODATION)
        ), List.of());
    }

    private static TripPlanItemResponse planItem(int order, TripPlanItemType type) {
        var start = LocalDateTime.of(2026, 9, 19, 8 + order, 0);
        return new TripPlanItemResponse(order, type, (long) order, null, type.name(), null,
                33.45 + order / 100.0, 126.3 + order / 100.0, start, start.plusMinutes(60), 60,
                SegmentTransportMode.RENTAL_CAR, null);
    }
}
