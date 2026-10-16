package com.loop.loop_backend.Venue.domain;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공연장 수용 인원. 스탠딩·좌석 배치에 따라 달라지는 공연장이 많아 범위("200~400")로도 둔다.
 * 숫자 두 개(min, max)로 저장하고, 화면에는 천 단위 쉼표를 넣은 텍스트("1,050~2,040")로 내보낸다.
 *
 * 입력·저장·출력 규칙을 이곳 한 곳에 둬서 관리자 API, CSV, 공개 응답이 같은 규칙을 쓰게 한다.
 * - 단일 값이면 max만 있다. 최솟값과 최댓값이 같으면 단일 값으로 줄인다("500~500" → "500").
 * - 최솟값만 있을 수는 없고, 최솟값이 최댓값보다 클 수 없다.
 */
public record VenueCapacity(Integer min, Integer max) {

    public static final VenueCapacity EMPTY = new VenueCapacity(null, null);

    /** 요청 검증용(@Pattern) 입력 형태: 빈 값, "숫자", "숫자~숫자" - 숫자 사이 쉼표·공백 허용. 최솟값 > 최댓값은 생성자가 막는다. */
    public static final String INPUT_PATTERN = "\\s*|\\s*\\d[\\d,\\s]*(~\\s*\\d[\\d,\\s]*)?";

    // 쉼표·공백을 지운 뒤 "숫자" 또는 "숫자~숫자"
    private static final Pattern FORMAT = Pattern.compile("(\\d{1,9})(?:~(\\d{1,9}))?");

    public VenueCapacity {
        if (min != null && max == null) {
            throw new IllegalArgumentException("수용 인원 최솟값만 있을 수는 없다");
        }
        if ((min != null && min < 0) || (max != null && max < 0)) {
            throw new IllegalArgumentException("수용 인원은 0 이상이다");
        }
        if (min != null && min > max) {
            throw new IllegalArgumentException("수용 인원 최솟값이 최댓값보다 크다: " + min + "~" + max);
        }
        if (min != null && min.equals(max)) {
            min = null;
        }
    }

    public static VenueCapacity of(Integer max) {
        return new VenueCapacity(null, max);
    }

    /**
     * "300", "200~400", "1,050 ~ 2,040" 형태를 읽는다. 빈 값이면 EMPTY.
     * 그 외 형태("약 300", "200~300~400", "-100")는 IllegalArgumentException(400).
     */
    public static VenueCapacity parse(String text) {
        if (text == null || text.isBlank()) return EMPTY;
        Matcher m = FORMAT.matcher(text.replace(",", "").replaceAll("\\s", ""));
        if (!m.matches()) {
            throw new IllegalArgumentException("수용 인원은 숫자 또는 '최솟값~최댓값' 형태다: " + text);
        }
        int first = Integer.parseInt(m.group(1));
        return m.group(2) == null
                ? of(first)
                : new VenueCapacity(first, Integer.parseInt(m.group(2)));
    }

    public boolean isEmpty() {
        return max == null;
    }

    /** 화면용 텍스트. "6,830", "1,050~2,040", 값이 없으면 null. */
    public String toText() {
        if (max == null) return null;
        return min == null ? format(max) : format(min) + "~" + format(max);
    }

    private static String format(int n) {
        return String.format(Locale.ROOT, "%,d", n);
    }
}