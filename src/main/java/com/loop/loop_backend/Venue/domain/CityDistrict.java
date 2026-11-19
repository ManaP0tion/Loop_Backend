package com.loop.loop_backend.Venue.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 공연장 주소는 시·구 단위까지만 둔다(AD-02). KOPIS 시설 주소처럼 도로명·번지까지 있는 주소에서
 * 앞쪽 행정구역(시·도 / 시·군 / 구)만 남긴다.
 * 예) "인천광역시 중구 공항문화로 127 (운서동)" → "인천광역시 중구"
 *     "경기도 성남시 분당구 …" → "경기도 성남시 분당구"
 *     "세종특별자치시 한누리대로 2130" → "세종특별자치시"
 */
public final class CityDistrict {

    // 시·도 → 시·군·구 → (일반구) 순서라 행정구역은 앞에서 최대 세 칸까지다.
    private static final int MAX_DIVISIONS = 3;

    private CityDistrict() {}

    /** 행정구역으로 읽히는 앞쪽 단어만 남긴다. 하나도 못 찾으면(예: "서울 송파구") 원래 주소를 그대로 둔다. */
    public static String from(String fullAddress) {
        if (fullAddress == null || fullAddress.isBlank()) return null;
        String[] words = fullAddress.trim().split("\\s+");
        List<String> divisions = new ArrayList<>();
        for (String word : words) {
            if (divisions.size() == MAX_DIVISIONS || !isAdministrativeDivision(word)) break;
            divisions.add(word);
        }
        return divisions.isEmpty() ? fullAddress.trim() : String.join(" ", divisions);
    }

    // 특별시·광역시·특별자치시·도·특별자치도·시·군·구가 모두 이 네 글자 중 하나로 끝난다. 도로명(…로, …길)과 번지는 걸리지 않는다.
    private static boolean isAdministrativeDivision(String word) {
        return word.endsWith("시") || word.endsWith("도") || word.endsWith("군") || word.endsWith("구");
    }
}