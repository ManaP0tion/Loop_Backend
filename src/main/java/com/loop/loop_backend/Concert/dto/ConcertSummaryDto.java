package com.loop.loop_backend.Concert.dto;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

// 공연 자체 정보만 담는 응답. 동행(Companion) 도메인을 조회하지 않는다 - 비로그인 조회를 포함해
// 콘서트를 보여주는 모든 화면(공연 탭 목록/상세/검색/아티스트별)이 이 DTO를 쓴다.
// 동행 인원수가 필요한 화면은 별도로 companionCount를 채워주는 DTO(ConcertResponseDto)를 쓸 것.
@Getter
@Builder
public class ConcertSummaryDto {

    private final Long concertId;
    private final Long artistId;
    private final String artistName;
    private final String kopisId;
    private final String title;
    private final String posterUrl;
    private final String venue;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final ConcertCategory category;
    private final String categoryDisplayName;

    public static ConcertSummaryDto from(Concert concert) {
        ConcertCategory category = concert.getCategory();
        return ConcertSummaryDto.builder()
                .concertId(concert.getId())
                .artistId(concert.getArtist() != null ? concert.getArtist().getId() : null)
                .artistName(concert.getArtist() != null ? concert.getArtist().getName() : null)
                .kopisId(concert.getKopisId())
                .title(concert.getTitle())
                .posterUrl(concert.getPosterUrl())
                .venue(concert.getVenue())
                .startDate(concert.getStartDate())
                .endDate(concert.getEndDate())
                .category(category)
                .categoryDisplayName(category != null ? category.getDisplayName() : null)
                .build();
    }
}