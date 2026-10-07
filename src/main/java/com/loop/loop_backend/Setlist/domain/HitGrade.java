package com.loop.loop_backend.Setlist.domain;

/** 적중률 등급 4구간(NO.60). 문구는 프론트(와이어프레임). LOW만 회색. */
public enum HitGrade {
    LOW,     // 0–49
    MID,     // 50–69
    HIGH,    // 70–84
    TOP;     // 85–100

    public static HitGrade of(int percent) {
        if (percent >= 85) return TOP;
        if (percent >= 70) return HIGH;
        if (percent >= 50) return MID;
        return LOW;
    }
}
