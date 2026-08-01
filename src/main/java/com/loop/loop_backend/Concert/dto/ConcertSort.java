package com.loop.loop_backend.Concert.dto;

import java.util.Comparator;

// 콘서트 목록 정렬 기준. 목록 자체가 이미 DTO로 만들어지므로 in-memory 정렬로 처리한다.
public enum ConcertSort {

    // 임박순: 시작일이 가까운 순(날짜 미정은 뒤로) — 기존 기본 정렬과 동일
    IMMINENT(Comparator.comparing(ConcertResponseDto::getStartDate,
            Comparator.nullsLast(Comparator.naturalOrder()))),

    // 최신순(LATEST): 미사용. id를 등록시각 대용으로 쓰던 방식이라 실제 "최신"과 맞지 않아 제거.
    // 다시 필요하면 Concert에 createdAt 추가 후 그 기준으로 되살릴 것.
    // LATEST(Comparator.comparing(ConcertResponseDto::getId).reversed()),

    // 인기순: 동행글 수 많은 순, 동률이면 임박순
    POPULAR(Comparator.comparingLong(ConcertResponseDto::getCompanionCount).reversed()
            .thenComparing(ConcertResponseDto::getStartDate,
                    Comparator.nullsLast(Comparator.naturalOrder())));

    private final Comparator<ConcertResponseDto> comparator;

    ConcertSort(Comparator<ConcertResponseDto> comparator) {
        this.comparator = comparator;
    }

    public Comparator<ConcertResponseDto> comparator() {
        return comparator;
    }
}
