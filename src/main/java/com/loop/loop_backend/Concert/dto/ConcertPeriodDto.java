package com.loop.loop_backend.Concert.dto;

// URL 직접 접근(딥링크) 등 프론트가 startDate/endDate를 아직 안 들고 있는 상태에서,
// id만으로 upcoming-detail/past-detail 중 뭘 호출할지 판단하기 위한 가벼운 조회 응답.
public record ConcertPeriodDto(ConcertPeriod period) {
}