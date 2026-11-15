package com.loop.loop_backend.Concert.dto;

// 공연 목록 조회 시점 구분. 정렬은 서버가 이 값 기준으로 고정한다(클라이언트가 sort를 고를 수 없음).
// UPCOMING → 가까운 날짜순, PAST → 최근 종료순.
public enum ConcertPeriod {
    UPCOMING,
    PAST
}