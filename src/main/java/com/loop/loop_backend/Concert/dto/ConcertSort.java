package com.loop.loop_backend.Concert.dto;

import java.util.Comparator;

// 콘서트 목록 정렬 기준. 목록 자체가 이미 DTO로 만들어지므로 in-memory 정렬로 처리한다.
public enum ConcertSort {

    // 임박순: 시작일이 가까운 순(날짜 미정은 뒤로) — 기존 기본 정렬과 동일
    IMMINENT(Comparator.comparing(ConcertResponseDto::getStartDate,
            Comparator.nullsLast(Comparator.naturalOrder()))),

    // 최신순: 최근 등록순. Concert에 createdAt이 없어 auto-increment id를 대용으로 사용
    // ponytail: id를 등록시각 대용으로 사용. 정확한 등록시각 정렬이 필요하면 Concert에 createdAt 추가
    LATEST(Comparator.comparing(ConcertResponseDto::getId).reversed()),

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
