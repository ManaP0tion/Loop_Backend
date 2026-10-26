package com.loop.loop_backend.Concert.dto;

import com.loop.loop_backend.Artist.dto.ArtistProductDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Setlist.dto.SetlistRankingResponse;
import com.loop.loop_backend.Concert.domain.ticket.ConcertGeneralSale;
import com.loop.loop_backend.Concert.domain.ticket.ConcertPresale;
import com.loop.loop_backend.TicketAlarm.domain.TicketAlarmType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

// 예정 공연 상세. 어드민이 입력한 값(공연장 관리의 공연장, DAY별 공연 시각, 선예매·일반예매, 숙소, 관련 상품)을 내려준다.
// 비공개 공연은 상세에 들어올 수 없다(서비스에서 403).
//
// 공연장 관리·공연 시각 입력이 생기기 전에 승인된 공연은 venue·showtimes를 KOPIS 값으로 대신 채운다
// (ConcertVenueDto, ConcertShowtimeDto 참고). 예매는 일시가 정해진 블록만 나가므로, 이전 공연의 KOPIS 예매처는
// 관리자가 예매 일시를 넣기 전까지 보이지 않는다.
//
// null 표기는 OpenAPI 3.0의 nullable=true 대신 3.1 방식(types에 "null" 포함)을 쓴다.
// nullable=true는 스펙엔 들어가지만 스웨거 UI가 타입 옆에 안 보여줘서, type 배열(예: {"string","null"})로
// 명확히 보이게 한다. 목록 필드는 비어 있으면 null이 아니라 빈 배열이다.
@Getter
@Builder
public class ConcertUpcomingDetailDto {

    @Schema(description = "콘서트 PK", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long concertId;

    @Schema(description = "아티스트 목록. 지금은 내한 공연 1명, 페스티벌은 빈 목록", requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<ConcertArtistDto> artists;

    @Schema(description = "공연명", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String title;

    @Schema(description = "공연 유형. J_POP_ARTIST(내한 공연) / JAPAN_FESTIVAL(페스티벌). " +
            "V2 이전 데이터는 DOMESTIC_ARTIST / DOMESTIC_FESTIVAL일 수 있다", example = "JAPAN_FESTIVAL",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final ConcertCategory category;

    @Schema(description = "포스터 이미지 URL", types = {"string", "null"})
    private final String posterUrl;

    @Schema(description = "공연장. 공연장 정보가 없는 공연은 null", types = {"object", "null"})
    private final ConcertVenueDto venue;

    @Schema(description = "공연 시작일. 날짜 미정이면 null", types = {"string", "null"})
    private final LocalDate startDate;

    @Schema(description = "공연 종료일. 날짜 미정이면 null", types = {"string", "null"})
    private final LocalDate endDate;

    // 필드명이 그대로 JSON 키(dday)가 되도록 dDay가 아니라 dday로 둔다.
    // (Lombok getDDay() -> Jackson이 "dday"로 직렬화 - 필드명이 dDay면 이 이름 불일치 때문에
    //  swagger가 이 필드의 @Schema를 못 찾아서 required/nullable이 스펙에 안 실렸다)
    @Schema(description = "오늘(KST) 기준 startDate까지 남은 일수. 당일 0, 이미 시작한 공연은 음수, 날짜 미정이면 null",
            types = {"integer", "null"})
    private final Integer dday;

    @Schema(description = "DAY별 공연 시각. 기간의 모든 DAY가 순서대로 나오고 미정인 DAY는 startTime이 null. 기간이 없으면 빈 목록",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<ConcertShowtimeDto> showtimes;

    @Schema(description = "선예매 목록. 예매 일시가 정해진 것만 일시 순으로, 없으면 빈 목록", requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<ConcertTicketSaleDto> presales;

    @Schema(description = "일반예매 목록. 예매 일시가 정해진 것만 일시 순으로, 없으면 빈 목록", requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<ConcertTicketSaleDto> generalSales;

    @Schema(description = "숙소 딥링크(공연장 단위). 공연장의 숙소 노출이 꺼져 있거나 공연장이 없으면 null(섹션 숨김)",
            types = {"string", "null"})
    private final String lodgingUrl;

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

    @Schema(description = "선예매 알림 켜짐 여부. 기본 false, 비로그인이면 항상 false. 켜 두었어도 presales가 비어 있으면 false",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean presaleAlarm;

    @Schema(description = "일반예매 알림 켜짐 여부. 기본 false, 비로그인이면 항상 false. 켜 두었어도 generalSales가 비어 있으면 false",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean generalSaleAlarm;

    @Schema(description = "단독 공연의 출연 정보 PK - 셋리스트·투표 API 경로(/performances/{performanceId})에 쓴다. " +
            "페스티벌은 null - 라인업의 헤드라이너 lineupId를 쓴다", types = {"integer", "null"})
    private final Long performanceId;

    @Schema(description = "단독 공연 예상 셋리스트 순위 미리보기(상위 5곡). 형태는 GET /api/concerts/{id}/performances/{performanceId}/setlist/ranking과 같고 songs만 최대 5곡. " +
            "투표 0건이면 songs 빈 목록, 페스티벌·아티스트 미지정 공연이면 highlightCount null(섹션 숨김) - 페스티벌 헤드라이너는 출연 정보별 API로 조회",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final SetlistRankingResponse setlistPreview;

    /**
     * @param today D-day 계산 기준일. "지금"을 이 클래스 안에서 정하지 않고 호출한 쪽(서비스)이 넘기게 해서,
     *              테스트에서 날짜를 고정해 검증할 수 있게 한다.
     * @param alarms 내가 켠 예매 알림 종류. 비로그인이면 빈 집합
     */
    public static ConcertUpcomingDetailDto from(Concert concert, LocalDate today, List<ConcertPresale> presales,
                                                List<ConcertGeneralSale> generalSales, boolean scrapped,
                                                Set<TicketAlarmType> alarms,
                                                Long performanceId, SetlistRankingResponse setlistPreview) {
        return ConcertUpcomingDetailDto.builder()
                .concertId(concert.getId())
                .artists(ConcertArtistDto.listOf(concert))
                .title(concert.getTitle())
                .category(concert.getCategory())
                .posterUrl(concert.getPosterUrl())
                .venue(ConcertVenueDto.of(concert))
                .startDate(concert.getStartDate())
                .endDate(concert.getEndDate())
                // 시작일이 없는 공연(날짜 미정)은 D-day를 계산할 수 없어 null.
                // 여러 날 공연이 이미 시작해 진행 중이면 음수가 나온다 - 표시 방식은 프론트가 정한다.
                .dday(concert.getStartDate() != null
                        ? (int) ChronoUnit.DAYS.between(today, concert.getStartDate())
                        : null)
                .showtimes(ConcertShowtimeDto.byDay(concert))
                .presales(ConcertTicketSaleDto.fromPresales(presales))
                .generalSales(ConcertTicketSaleDto.fromGeneralSales(generalSales))
                .lodgingUrl(concert.getLinkedVenue() == null ? null : concert.getLinkedVenue().visibleLodgingUrl())
                .officialSiteUrl(concert.getOfficialSiteUrl())
                .instagramUrl(concert.getInstagramUrl())
                .xUrl(concert.getXUrl())
                .products(ArtistProductDto.listOf(concert.getArtist()))
                .scrapped(scrapped)
                .presaleAlarm(alarms.contains(TicketAlarmType.PRESALE))
                .generalSaleAlarm(alarms.contains(TicketAlarmType.GENERAL_SALE))
                .performanceId(performanceId)
                .setlistPreview(setlistPreview)
                .build();
    }
}