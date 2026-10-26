package com.loop.loop_backend.Concert.dto;

import com.loop.loop_backend.Artist.dto.ArtistProductDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Setlist.dto.SetlistResultResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

// 지난 공연 상세. 예매 정보·예매 알림은 이제 와서 의미가 없어 제외 — 공연명/포스터/아티스트(공식 링크 포함)/공연장/날짜/공연 시각/공식 사이트/관련 상품
// + 다음에 볼 만한 다가오는 내한 공연.
// 공연장·공연 시각은 예정 공연 상세와 같은 형태와 규칙이다(ConcertVenueDto, ConcertShowtimeDto).
@Getter
@Builder
public class ConcertPastDetailDto {

    @Schema(description = "콘서트 PK", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long concertId;

    @Schema(description = "공연명", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String title;

    @Schema(description = "공연 유형. J_POP_ARTIST(내한 공연) / JAPAN_FESTIVAL(페스티벌). " +
            "V2 이전 데이터는 DOMESTIC_ARTIST / DOMESTIC_FESTIVAL일 수 있다", example = "JAPAN_FESTIVAL",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final ConcertCategory category;

    @Schema(description = "포스터 이미지 URL", types = {"string", "null"})
    private final String posterUrl;

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

    @Schema(description = "특설 공식 사이트 URL(공연 공식 홈페이지). 없으면 null(버튼 숨김)", types = {"string", "null"})
    private final String officialSiteUrl;

    @Schema(description = "공연 공식 인스타그램 URL(페스티벌 공식 채널용). 없으면 null(버튼 숨김)", types = {"string", "null"})
    private final String instagramUrl;

    // Lombok getXUrl()을 Jackson이 "xurl"로 읽어서(필드 xUrl과 따로 두 번 나감) getter에 키 이름을 고정한다.
    // 키가 getter 기준이 되므로 스웨거 설명(@Schema)도 getter에 붙여야 스펙에 실린다.
    @Getter(onMethod_ = {@JsonProperty("xUrl"),
            @Schema(description = "공연 공식 X URL(페스티벌 공식 채널용). 없으면 null(버튼 숨김)", types = {"string", "null"})})
    private final String xUrl;

    @Schema(description = "관련 상품(공연 아티스트에 등록된 상품). 관리자가 입력한 순서대로, 아티스트가 없거나 상품이 없으면 빈 목록(섹션 숨김)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<ArtistProductDto> products;

    @Schema(description = "내가 스크랩한 공연인지. 비로그인 조회면 항상 false", requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean scrapped;

    @Schema(description = "단독 공연의 출연 정보 PK - 셋리스트·투표 API 경로(/performances/{performanceId})에 쓴다. " +
            "페스티벌은 null - 라인업의 헤드라이너 lineupId를 쓴다", types = {"integer", "null"})
    private final Long performanceId;

    @Schema(description = "단독 공연 셋리스트 결과(내 적중률 mine·실제 셋리스트 songs 등). 형태는 GET /api/concerts/{id}/performances/{performanceId}/setlist/result와 같다. " +
            "실제 셋리스트 저장 전이거나 페스티벌·아티스트 미지정 공연이면 ready=false(대기 문구), 비로그인·미투표면 mine null. " +
            "페스티벌 헤드라이너는 출연 정보별 API로 조회",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final SetlistResultResponse setlistResult;

    @Schema(description = "다가오는 J-POP 내한 공연. 목록 조회(GET /api/concerts)와 같은 형태로, 내한 탭 예정 목록 순서(가까운 날짜순, 날짜 미정은 뒤)의 " +
            "공개 공연 최대 4건. 없으면 빈 목록(섹션 숨김)", requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<ConcertSummaryDto> upcomingConcerts;

    public static ConcertPastDetailDto from(Concert concert, boolean scrapped, Long performanceId,
                                            SetlistResultResponse setlistResult, List<ConcertSummaryDto> upcomingConcerts) {
        return ConcertPastDetailDto.builder()
                .concertId(concert.getId())
                .title(concert.getTitle())
                .category(concert.getCategory())
                .posterUrl(concert.getPosterUrl())
                .artistName(concert.getArtist() != null ? concert.getArtist().getName() : null)
                .artists(ConcertArtistDto.listOf(concert))
                .venue(ConcertVenueDto.of(concert))
                .startDate(concert.getStartDate())
                .endDate(concert.getEndDate())
                .showtimes(ConcertShowtimeDto.byDay(concert))
                .officialSiteUrl(concert.getOfficialSiteUrl())
                .instagramUrl(concert.getInstagramUrl())
                .xUrl(concert.getXUrl())
                .products(ArtistProductDto.listOf(concert.getArtist()))
                .scrapped(scrapped)
                .performanceId(performanceId)
                .setlistResult(setlistResult)
                .upcomingConcerts(upcomingConcerts)
                .build();
    }
}