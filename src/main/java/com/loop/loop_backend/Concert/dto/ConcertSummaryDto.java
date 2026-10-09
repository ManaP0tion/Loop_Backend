package com.loop.loop_backend.Concert.dto;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

// 공연 자체 정보만 담는 응답. 동행(Companion) 도메인을 조회하지 않는다 - 비로그인 조회를 포함해
// 콘서트를 보여주는 모든 화면(공연 탭 목록/상세/검색/아티스트별/스크랩)이 이 DTO를 쓴다.
// 동행 인원수가 필요한 화면은 별도로 companionCount를 채워주는 DTO(ConcertResponseDto)를 쓸 것.
//
// 목록 쿼리는 아티스트·공연장을 fetch join으로 함께 가져와야 한다 - 이 DTO가 둘 다 읽어서, 아니면 공연마다 쿼리가 더 나간다(N+1).
@Getter
@Builder
public class ConcertSummaryDto {

    private final Long concertId;
    private final Long artistId;
    private final String artistName;
    private final String kopisId;
    private final String title;
    private final String posterUrl;

    @Schema(description = "공연장. 공연장 정보가 없는 공연은 null", types = {"object", "null"})
    private final ConcertVenueDto venue;

    private final LocalDate startDate;
    private final LocalDate endDate;
    private final ConcertCategory category;
    private final String categoryDisplayName;

    @Schema(description = "오픈 예정(비공개) 공연인지. true면 썸네일을 회색으로 보여주고 상세 진입을 막는다(상세 API는 403)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean preparing;

    public static ConcertSummaryDto from(Concert concert) {
        ConcertCategory category = concert.getCategory();
        return ConcertSummaryDto.builder()
                .concertId(concert.getId())
                .artistId(concert.getArtist() != null ? concert.getArtist().getId() : null)
                .artistName(concert.getArtist() != null ? concert.getArtist().getName() : null)
                .kopisId(concert.getKopisId())
                .title(concert.getTitle())
                .posterUrl(concert.getPosterUrl())
                .venue(ConcertVenueDto.of(concert))
                .startDate(concert.getStartDate())
                .endDate(concert.getEndDate())
                .category(category)
                .categoryDisplayName(category != null ? category.getDisplayName() : null)
                .preparing(!concert.isPublished())
                .build();
    }
}