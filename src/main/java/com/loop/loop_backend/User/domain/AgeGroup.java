package com.loop.loop_backend.User.domain;

import java.time.LocalDate;
import java.time.Period;

public enum AgeGroup {
    TEENS("10대"),
    EARLY_TWENTIES("20대 초반"),
    LATE_TWENTIES("20대 후반"),
    EARLY_THIRTIES("30대 초반"),
    LATE_THIRTIES("30대 후반"),
    EARLY_FORTIES("40대 초반"),
    LATE_FORTIES("40대 후반"),
    FIFTIES_OVER("50대 이상");

    private final String label;

    AgeGroup(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static AgeGroup from(LocalDate birthDate) {
        if (birthDate == null) {
            return null;
        }

        int age = Period.between(birthDate, LocalDate.now()).getYears(); // 만 나이

        if (age < 20) {
            return TEENS;
        }
        int decade = (age / 10) * 10;
        boolean early = (age % 10) < 5;

        return switch (decade) {
            case 20 -> early ? EARLY_TWENTIES : LATE_TWENTIES;
            case 30 -> early ? EARLY_THIRTIES : LATE_THIRTIES;
            case 40 -> early ? EARLY_FORTIES : LATE_FORTIES;
            default -> FIFTIES_OVER;
        };

    }
}
