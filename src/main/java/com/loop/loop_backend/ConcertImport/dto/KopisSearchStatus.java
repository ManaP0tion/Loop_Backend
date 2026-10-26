package com.loop.loop_backend.ConcertImport.dto;

/**
 * KOPIS 검색 결과의 우리 쪽 등록 상태. 위에서부터 먼저 해당하는 값 하나만 나간다.
 * - REGISTERED: 이 KOPIS 공연으로 만든 공연이 있다 (직접 등록하면 409)
 * - PENDING: 검토 대기열에 있다 (직접 등록하면 그 행으로 바로 승인)
 * - REJECTED: 검토 대기열에서 거절됐다 (직접 등록하면 다시 열어 승인)
 * - NEW: 우리 쪽에 아무 기록이 없다
 */
public enum KopisSearchStatus {
    REGISTERED,
    PENDING,
    REJECTED,
    NEW
}