package com.loop.loop_backend.Concert.dto;

import com.loop.loop_backend.Concert.domain.Concert;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

// 지난 공연 상세. 예매/공연장 상세 정보는 이제 와서 의미가 없어 제외 — 공연명/아티스트/공연장/날짜만.
// 추후 셋리스트 기능이 붙으면 이 DTO에 필드가 추가될 예정.
@Getter
@Builder
public class ConcertPastDetailDto {

    private final Long concertId;
    private final String title;
    private final String artistName;
    private final String venue; // 공연장 이름
    private final LocalDate startDate;
    private final LocalDate endDate;

    public static ConcertPastDetailDto from(Concert concert) {
        return ConcertPastDetailDto.builder()
                .concertId(concert.getId())
                .title(concert.getTitle())
                .artistName(concert.getArtist() != null ? concert.getArtist().getName() : null)
                .venue(concert.getVenue())
                .startDate(concert.getStartDate())
                .endDate(concert.getEndDate())
                .build();
    }
}