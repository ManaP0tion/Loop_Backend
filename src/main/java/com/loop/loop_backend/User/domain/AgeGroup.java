package com.loop.loop_backend.User.domain;

import java.time.LocalDate;
import java.time.Period;

public enum AgeGroup {
    NINETEEN_TO_TWENTY_FOUR("19~24세", 0, 24),
    TWENTY_FIVE_TO_TWENTY_NINE("25~29세", 25, 29),
    THIRTY_TO_THIRTY_FOUR("30~34세", 30, 34),
    THIRTY_FIVE_TO_THIRTY_NINE("35~39세", 35, 39),
    FORTY_PLUS("40세 이상", 40, null),
    ANY("전체", null, null); // 필터 전용 와일드카드 - 실제 사용자의 연령대로 계산되지 않음

    private final String label;
    private final Integer minAge;
    private final Integer maxAge; // null이면 상한 없음 (ANY는 min/max 둘 다 null)

    AgeGroup(String label, Integer minAge, Integer maxAge) {
        this.label = label;
        this.minAge = minAge;
        this.maxAge = maxAge;
    }

    public String getLabel() {
        return label;
    }

    public Integer getMinAge() {
        return minAge;
    }

    public Integer getMaxAge() {
        return maxAge;
    }

    public static AgeGroup from(LocalDate birthDate) {
        if (birthDate == null) {
            return null;
        }

        int age = Period.between(birthDate, LocalDate.now()).getYears(); // 만 나이

        if (age <= 24) {
            return NINETEEN_TO_TWENTY_FOUR;
        }
        if (age <= 29) {
            return TWENTY_FIVE_TO_TWENTY_NINE;
        }
        if (age <= 34) {
            return THIRTY_TO_THIRTY_FOUR;
        }
        if (age <= 39) {
            return THIRTY_FIVE_TO_THIRTY_NINE;
        }
        return FORTY_PLUS;
    }
}
