package com.travel.trip.plan.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 자유 입력에서 일정 생성에 실제로 반영하는 정보는
 * "관광지 이름 + N일차" 하나뿐이다.
 *
 * 원문 자체를 Bedrock에 넘기지 않고 이 구조화된 결과만 넘겨서
 * 다른 자연어 표현이 기존 일정 생성 규칙을 흔들지 않게 한다.
 */
public final class TripPromptDayConstraintParser {

    private static final Pattern DAY_PATTERN =
            Pattern.compile("([1-9]\\d*)일차");

    /**
     * 쉼표/마침표/개행/"그리고" 단위로 먼저 분리한다.
     * "새별오름 2일차, 한담해안산책로 3일차"처럼 여러 요청이 있을 때
     * 앞 관광지의 일차가 뒤 관광지에 잘못 붙는 것을 방지한다.
     */
    private static final Pattern CLAUSE_SEPARATOR =
            Pattern.compile("[,，;.!?\\n\\r]+|\\s*그리고\\s*");

    private TripPromptDayConstraintParser() {
    }

    /**
     * 이전 테스트/호출부와의 호환을 위한 단일 관광지 조회 API다.
     * 실제 구현은 parse(...)와 동일한 규칙을 사용하므로 프롬프트 해석 로직이
     * 두 군데로 갈라지지 않는다.
     */
    static Integer requestedDayFor(
            String prompt,
            String placeName,
            int totalDays
    ) {
        if (placeName == null || placeName.isBlank()) {
            return null;
        }

        return parse(
                prompt,
                totalDays,
                List.of(new NamedAttraction(1L, placeName))
        ).stream()
                .findFirst()
                .map(DayConstraint::dayNumber)
                .orElse(null);
    }

    public static List<DayConstraint> parse(
            String prompt,
            int totalDays,
            List<NamedAttraction> attractions
    ) {
        if (prompt == null || prompt.isBlank()
                || totalDays <= 0
                || attractions == null
                || attractions.isEmpty()) {
            return List.of();
        }

        Map<Long, DayConstraint> selectedByAttractionId = new LinkedHashMap<>();

        for (String clause : CLAUSE_SEPARATOR.split(prompt)) {
            if (clause == null || clause.isBlank()) {
                continue;
            }

            for (DayConstraint constraint : parseClause(clause, totalDays, attractions)) {
                selectedByAttractionId.putIfAbsent(
                        constraint.attractionId(),
                        constraint
                );
            }
        }

        List<DayConstraint> result = new ArrayList<>(selectedByAttractionId.values());
        result.sort(Comparator
                .comparingInt(DayConstraint::dayNumber)
                .thenComparing(DayConstraint::attractionName));

        return List.copyOf(result);
    }

    private static List<DayConstraint> parseClause(
            String clause,
            int totalDays,
            List<NamedAttraction> attractions
    ) {
        String normalizedPrompt = normalize(clause);
        if (normalizedPrompt.isBlank()) {
            return List.of();
        }

        List<DayOccurrence> days = findDays(normalizedPrompt, totalDays);
        if (days.isEmpty()) {
            return List.of();
        }

        List<CandidateOccurrence> matches = new ArrayList<>();
        Set<Long> seenCandidateIds = new HashSet<>();

        attractions.stream()
                .filter(item -> item != null && item.id() != null && item.name() != null)
                .sorted(Comparator
                        .comparingInt((NamedAttraction item) -> normalize(item.name()).length())
                        .reversed()
                        .thenComparing(NamedAttraction::id))
                .forEach(item -> {
                    if (!seenCandidateIds.add(item.id())) {
                        return;
                    }

                    String normalizedName = normalize(item.name());
                    if (normalizedName.length() < 3) {
                        return;
                    }

                    int fromIndex = 0;
                    while (fromIndex < normalizedPrompt.length()) {
                        int index = normalizedPrompt.indexOf(normalizedName, fromIndex);
                        if (index < 0) {
                            break;
                        }

                        DayOccurrence nearest = nearestDay(
                                index,
                                index + normalizedName.length(),
                                days
                        );

                        if (nearest != null) {
                            matches.add(new CandidateOccurrence(
                                    item,
                                    index,
                                    index + normalizedName.length(),
                                    nearest
                            ));
                        }

                        fromIndex = index + normalizedName.length();
                    }
                });

        // 긴 관광지 이름을 먼저 확정하여 이름이 겹치는 짧은 후보가 같이 잡히는 것을 막는다.
        matches.sort(Comparator
                .comparingInt((CandidateOccurrence item) -> item.end() - item.start())
                .reversed()
                .thenComparingInt(item -> item.day().distance()));

        List<IntRange> occupied = new ArrayList<>();
        Set<Long> selectedIds = new HashSet<>();
        List<DayConstraint> result = new ArrayList<>();

        for (CandidateOccurrence match : matches) {
            if (selectedIds.contains(match.attraction().id())
                    || overlaps(match.start(), match.end(), occupied)) {
                continue;
            }

            selectedIds.add(match.attraction().id());
            occupied.add(new IntRange(match.start(), match.end()));
            result.add(new DayConstraint(
                    match.attraction().id(),
                    match.attraction().name(),
                    match.day().dayNumber()
            ));
        }

        return result;
    }

    private static List<DayOccurrence> findDays(
            String normalizedPrompt,
            int totalDays
    ) {
        List<DayOccurrence> result = new ArrayList<>();
        Matcher matcher = DAY_PATTERN.matcher(normalizedPrompt);

        while (matcher.find()) {
            int dayNumber;
            try {
                dayNumber = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                continue;
            }

            if (dayNumber < 1 || dayNumber > totalDays) {
                continue;
            }

            result.add(new DayOccurrence(
                    dayNumber,
                    matcher.start(),
                    matcher.end(),
                    0
            ));
        }

        return result;
    }

    private static DayOccurrence nearestDay(
            int attractionStart,
            int attractionEnd,
            List<DayOccurrence> days
    ) {
        DayOccurrence best = null;
        int bestDistance = Integer.MAX_VALUE;
        boolean bestIsAfter = false;

        for (DayOccurrence day : days) {
            boolean isAfter = day.start() >= attractionEnd;
            int distance;

            if (day.end() <= attractionStart) {
                distance = attractionStart - day.end();
            } else if (isAfter) {
                distance = day.start() - attractionEnd;
            } else {
                distance = 0;
            }

            // 거리가 같으면 "관광지 -> N일차" 표현을 우선한다.
            if (distance < bestDistance
                    || (distance == bestDistance && isAfter && !bestIsAfter)) {
                bestDistance = distance;
                bestIsAfter = isAfter;
                best = new DayOccurrence(
                        day.dayNumber(),
                        day.start(),
                        day.end(),
                        distance
                );
            }
        }

        return best;
    }

    private static boolean overlaps(
            int start,
            int end,
            List<IntRange> occupied
    ) {
        return occupied.stream()
                .anyMatch(range -> start < range.end() && end > range.start());
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^0-9a-z가-힣]", "");
    }

    public record NamedAttraction(
            Long id,
            String name
    ) {
    }

    public record DayConstraint(
            Long attractionId,
            String attractionName,
            int dayNumber
    ) {
    }

    private record DayOccurrence(
            int dayNumber,
            int start,
            int end,
            int distance
    ) {
    }

    private record CandidateOccurrence(
            NamedAttraction attraction,
            int start,
            int end,
            DayOccurrence day
    ) {
    }

    private record IntRange(
            int start,
            int end
    ) {
    }
}
