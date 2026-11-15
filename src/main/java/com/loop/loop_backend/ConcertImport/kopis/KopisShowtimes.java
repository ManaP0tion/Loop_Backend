package com.loop.loop_backend.ConcertImport.kopis;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KOPIS 공연 시간 안내(dtguidance)를 DAY별 공연 시각 기본값으로 바꾼다. 관리자가 수정 화면에서 고칠 수 있는 기본값이다.
 *
 * KOPIS는 날짜별 시각이 아니라 "요일(시각)" 문장을 준다. 실제 대중음악 공연 150건(2026-10 조회)의 형식:
 *   토요일(19:00)                                   - 요일 하나 (80%)
 *   토요일(18:00), 일요일(17:00)                    - 여러 요일
 *   토요일 ~ 일요일(17:00)                          - 요일 범위
 *   HOL(18:00), 토요일 ~ 일요일(17:00)              - 공휴일. 요일로 못 채운 날짜가 공휴일이었다(예: 10/9 한글날)
 *   금요일(13:00,18:00)                             - 하루 여러 회차 (5%, 내한 공연에는 없었음)
 * 규칙: 공연 기간의 날짜마다 그 요일의 묶음을 찾고, 없으면 HOL 묶음을 쓴다. 시각이 하나면 그 시각,
 * 여러 회차이거나 해당 묶음이 없거나 시각을 못 읽으면 그 DAY는 비운다(null). 문장을 전혀 못 읽어도 승인은 막지 않는다.
 */
public final class KopisShowtimes {

    // "토요일", "토요일 ~ 일요일", "HOL" + "(시각들)"
    private static final Pattern GROUP = Pattern.compile(
            "(HOL|([월화수목금토일])요일(?:\\s*~\\s*([월화수목금토일])요일)?)\\s*\\(([^)]*)\\)");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");
    private static final String WEEKDAYS = "월화수목금토일"; // DayOfWeek 순서(MONDAY=1)와 같다

    private KopisShowtimes() {}

    /**
     * 공연 기간의 DAY 순서대로 시작 시각을 돌려준다(개수 = 공연 일수, 못 정한 DAY는 null).
     * 기간을 모르면 DAY를 만들 수 없어 빈 목록이다.
     */
    public static List<LocalTime> startTimesByDay(String dtguidance, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) return List.of();

        Map<DayOfWeek, List<LocalTime>> byWeekday = new EnumMap<>(DayOfWeek.class);
        List<LocalTime> holiday = null;
        Matcher m = GROUP.matcher(dtguidance == null ? "" : dtguidance);
        while (m.find()) {
            List<LocalTime> times = parseTimes(m.group(4));
            if (m.group(1).equals("HOL")) {
                if (holiday == null) holiday = times;
                continue;
            }
            DayOfWeek from = weekday(m.group(2));
            DayOfWeek to = m.group(3) != null ? weekday(m.group(3)) : from;
            for (DayOfWeek d = from; ; d = d.plus(1)) {
                byWeekday.putIfAbsent(d, times);
                if (d == to) break;
            }
        }

        int days = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
        List<LocalTime> result = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            List<LocalTime> times = byWeekday.getOrDefault(startDate.plusDays(i).getDayOfWeek(), holiday);
            result.add(times != null && times.size() == 1 ? times.get(0) : null);
        }
        return result;
    }

    /** "18:00" → [18:00], "13:00,18:00" → [13:00, 18:00]. 하나라도 못 읽으면 빈 목록(그 요일은 비운다). */
    private static List<LocalTime> parseTimes(String raw) {
        List<LocalTime> times = new ArrayList<>();
        for (String part : raw.split(",")) {
            try {
                times.add(LocalTime.parse(part.trim(), TIME));
            } catch (DateTimeParseException e) {
                return List.of();
            }
        }
        return times;
    }

    private static DayOfWeek weekday(String koreanDay) {
        return DayOfWeek.of(WEEKDAYS.indexOf(koreanDay) + 1);
    }
}