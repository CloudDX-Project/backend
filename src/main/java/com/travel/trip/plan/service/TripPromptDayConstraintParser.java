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
     * 강한 구두점/개행/"그리고" 단위로 먼저 분리한다.
     * 단, "한담해안산책로!! 3일차"처럼 구두점 때문에 관광지와 일차가
     * 서로 다른 조각으로 갈라진 경우에는 아래 splitClauses(...)에서 다시 합친다.
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

        for (String clause : splitClauses(prompt, totalDays, attractions)) {
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

    /**
     * 일반 문장 경계는 유지하되, 관광지명과 일차만 따로 떨어진 인접 조각은
     * 하나의 요청으로 다시 합친다.
     *
     * 예)
     * - "한담해안산책로!! 3일차" -> "한담해안산책로 3일차"
     * - "3일차. 한담해안산책로" -> "3일차 한담해안산책로"
     * - "새별오름 2일차. 한담해안산책로 3일차" -> 두 요청 그대로 유지
     */
    private static List<String> splitClauses(
            String prompt,
            int totalDays,
            List<NamedAttraction> attractions
    ) {
        List<String> rawParts = new ArrayList<>();
        for (String part : CLAUSE_SEPARATOR.split(prompt)) {
            if (part != null && !part.isBlank()) {
                rawParts.add(part.trim());
            }
        }

        if (rawParts.isEmpty()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        int index = 0;

        while (index < rawParts.size()) {
            String current = rawParts.get(index);
            boolean currentHasDay = containsValidDay(current, totalDays);
            boolean currentHasAttraction = containsKnownAttraction(current, attractions);

            if (index + 1 < rawParts.size()) {
                String next = rawParts.get(index + 1);
                boolean nextHasDay = containsValidDay(next, totalDays);
                boolean nextHasAttraction = containsKnownAttraction(next, attractions);

                boolean placeThenDay =
                        currentHasAttraction && !currentHasDay
                                && !nextHasAttraction && nextHasDay;

                boolean dayThenPlace =
                        currentHasDay && !currentHasAttraction
                                && nextHasAttraction && !nextHasDay;

                if (placeThenDay || dayThenPlace) {
                    result.add(current + " " + next);
                    index += 2;
                    continue;
                }
            }

            result.add(current);
            index++;
        }

        return result;
    }

    private static boolean containsValidDay(
            String value,
            int totalDays
    ) {
        Matcher matcher = DAY_PATTERN.matcher(normalize(value));
        while (matcher.find()) {
            try {
                int day = Integer.parseInt(matcher.group(1));
                if (day >= 1 && day <= totalDays) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // 다음 일차 표현을 계속 확인한다.
            }
        }
        return false;
    }

    private static boolean containsKnownAttraction(
            String value,
            List<NamedAttraction> attractions
    ) {
        if (attractions == null || attractions.isEmpty()) {
            return false;
        }

        String normalizedValue = normalize(value);
        if (normalizedValue.isBlank()) {
            return false;
        }

        return attractions.stream()
                .filter(item -> item != null && item.name() != null)
                .map(item -> normalize(item.name()))
                .filter(name -> name.length() >= 3)
                .anyMatch(normalizedValue::contains);
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
