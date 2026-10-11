package com.loop.loop_backend.Concert.dto;

import com.loop.loop_backend.Concert.domain.Concert;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

// 지난 공연 상세. 예매 정보·예매 알림은 이제 와서 의미가 없어 제외 — 공연명/아티스트(공식 링크 포함)/공연장/날짜/공연 시각/공식 사이트.
// 공연장·공연 시각은 예정 공연 상세와 같은 형태와 규칙이다(ConcertVenueDto, ConcertShowtimeDto).
// 추후 셋리스트 기능이 붙으면 이 DTO에 필드가 추가될 예정.
@Getter
@Builder
public class ConcertPastDetailDto {

    @Schema(description = "콘서트 PK", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long concertId;

    @Schema(description = "공연명", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String title;

    @Schema(description = "아티스트명. 아티스트가 없는 공연(페스티벌 등)은 null. artists와 같은 값 - 기존 화면 호환용으로 남겨 둔다",
            types = {"string", "null"})
    private final String artistName;

    @Schema(description = "아티스트 목록(공식 SNS·홈페이지 포함). 지금은 내한 공연 1명, 페스티벌은 빈 목록",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<ConcertArtistDto> artists;

    @Schema(description = "공연장. 공연장 정보가 없는 공연은 null", types = {"object", "null"})
    private final ConcertVenueDto venue;

    @Schema(description = "공연 시작일", requiredMode = Schema.RequiredMode.REQUIRED)
    private final LocalDate startDate;

    @Schema(description = "공연 종료일", types = {"string", "null"})
    private final LocalDate endDate;

    @Schema(description = "DAY별 공연 시각. 기간의 모든 DAY가 순서대로 나오고 미정인 DAY는 startTime이 null",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<ConcertShowtimeDto> showtimes;

    @Schema(description = "특설 공식 사이트 URL. 없으면 null(버튼 숨김)", types = {"string", "null"})
    private final String officialSiteUrl;

    @Schema(description = "내가 스크랩한 공연인지. 비로그인 조회면 항상 false", requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean scrapped;

    public static ConcertPastDetailDto from(Concert concert, boolean scrapped) {
        return ConcertPastDetailDto.builder()
                .concertId(concert.getId())
                .title(concert.getTitle())
                .artistName(concert.getArtist() != null ? concert.getArtist().getName() : null)
                .artists(ConcertArtistDto.listOf(concert))
                .venue(ConcertVenueDto.of(concert))
                .startDate(concert.getStartDate())
                .endDate(concert.getEndDate())
                .showtimes(ConcertShowtimeDto.byDay(concert))
                .officialSiteUrl(concert.getOfficialSiteUrl())
                .scrapped(scrapped)
                .build();
    }
}