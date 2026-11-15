package com.loop.loop_backend.Concert.domain;

/**
 * 공연별 동행 기능 상태.
 * 미오픈(한 번도 연 적 없음)과 종료(열었다가 닫음)를 구분한다 - 화면이 다르다(미오픈은 동행 탭에 안 보이고, 종료는 흐리게 보인다).
 */
public enum CompanionStatus {
    NOT_OPENED,  // 미오픈
    OPEN,        // 모집 중
    CLOSED       // 종료
}
