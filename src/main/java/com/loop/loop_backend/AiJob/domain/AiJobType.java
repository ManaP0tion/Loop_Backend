package com.loop.loop_backend.AiJob.domain;

/**
 * AI 워커가 처리하는 작업 종류. 워커는 claim 때 처리할 수 있는 종류를 모두 보낸다.
 * 아직 어드민에서 만들 수 없는 종류(SETLIST_ACTUAL)도 여기 둬야 한다 - 모르는 값이 섞이면 claim 자체가 실패하기 때문.
 * 어드민에서 만들 수 있는지는 adminCreatable로 가른다.
 */
public enum AiJobType {
    CONCERT_ENRICH(true),   // 티켓 정보(선예매·일반 예매 등) 찾기
    SETLIST_ACTUAL(false),  // 실제 셋리스트 찾기 - 1차 보류(payload 만들기 미구현)
    FESTIVAL_LINEUP(true);  // 페스티벌 라인업 찾기

    private final boolean adminCreatable;

    AiJobType(boolean adminCreatable) {
        this.adminCreatable = adminCreatable;
    }

    public boolean isAdminCreatable() {
        return adminCreatable;
    }
}