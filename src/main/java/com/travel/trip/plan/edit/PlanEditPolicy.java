package com.travel.trip.plan.edit;

import com.travel.trip.plan.dto.TripPlanDayResponse;
import com.travel.trip.plan.dto.TripPlanItemResponse;
import com.travel.trip.plan.type.TripPlanItemType;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class PlanEditPolicy {
    public static boolean editable(TripPlanItemResponse item) {
        return item.type() == TripPlanItemType.ATTRACTION || item.type() == TripPlanItemType.RESTAURANT
                || item.type() == TripPlanItemType.CAFE;
    }

    public static String key(int dayNumber, int order) { return dayNumber + ":" + order; }

    public Map<String, TripPlanItemResponse> validate(List<TripPlanDayResponse> original, PlanEditRequest request) {
        if (request.days().size() != original.size()) {
            throw PlanEditException.invalid("여행 날짜를 추가하거나 삭제할 수 없습니다.");
        }
        Map<String, TripPlanItemResponse> source = new HashMap<>();
        for (var day : original) {
            if (day.items().isEmpty() || editable(day.items().getFirst()) || editable(day.items().getLast())) {
                throw PlanEditException.invalid("시작·종료 기준 일정이 없습니다. 일정을 다시 생성해주세요.");
            }
            for (var item : day.items()) source.put(key(day.dayNumber(), item.order()), item);
        }
        Set<String> used = new HashSet<>();
        int total = 0;
        for (int i = 0; i < original.size(); i++) {
            var before = original.get(i);
            var after = request.days().get(i);
            if (!Objects.equals(before.dayNumber(), after.dayNumber()) || after.items().isEmpty()) {
                throw PlanEditException.invalid("일차의 순서나 시작·종료 일정을 변경할 수 없습니다.");
            }
            List<String> protectedKeys = before.items().stream().filter(item -> !editable(item))
                    .map(item -> key(before.dayNumber(), item.order())).toList();
            List<String> submittedProtected = new ArrayList<>();
            for (var spec : after.items()) {
                if (++total > 150) throw PlanEditException.invalid("한 여행은 최대 150개 일정까지 편집할 수 있습니다.");
                if (spec.itemKey() == null) {
                    if (spec.replacement() == null) throw PlanEditException.invalid("추가할 장소를 선택해주세요.");
                    continue;
                }
                var item = source.get(spec.itemKey());
                if (item == null || !used.add(spec.itemKey())) {
                    throw PlanEditException.invalid("알 수 없거나 중복된 일정입니다. 최신 일정을 다시 불러오세요.");
                }
                if (!editable(item)) {
                    submittedProtected.add(spec.itemKey());
                    if (spec.replacement() != null || spec.stayMinutes() != null || spec.startTime() != null) {
                        throw PlanEditException.invalid("항공·공항·숙소 일정은 직접 수정할 수 없습니다.");
                    }
                }
            }
            if (!protectedKeys.equals(submittedProtected)
                    || !Objects.equals(after.items().getFirst().itemKey(), key(before.dayNumber(), before.items().getFirst().order()))
                    || !Objects.equals(after.items().getLast().itemKey(), key(before.dayNumber(), before.items().getLast().order()))) {
                throw PlanEditException.invalid("항공·숙소 등 고정 일정의 일차·순서와 하루 시작·종료 위치를 유지해주세요.");
            }
        }
        return source;
    }
}
