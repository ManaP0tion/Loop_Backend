package com.loop.loop_backend.Setlist.domain;

/** 셋리스트 구분. 지난 셋리스트(공연 전) 2종 + 실제 셋리스트(공연 후). */
public enum SetlistType {
    RECENT,          // 최근 공연 - 투어와 무관하게 아티스트의 가장 최근 공연
    PREVIOUS_VISIT,  // 지난 내한
    ACTUAL;          // 실제 셋리스트 - 저장 시 적중률·결과 메일 기준

    public boolean isPast() {
        return this != ACTUAL;
    }
}
