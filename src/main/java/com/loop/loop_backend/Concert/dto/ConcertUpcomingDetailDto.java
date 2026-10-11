package com.loop.loop_backend.Concert.dto;

import com.loop.loop_backend.Concert.domain.Concert;
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

    @Schema(description = "숙소 딥링크. 숙소 섹션을 노출하지 않는 공연은 null(섹션 숨김)", types = {"string", "null"})
    private final String lodgingUrl;

    @Schema(description = "특설 공식 사이트 URL. 없으면 null(버튼 숨김)", types = {"string", "null"})
    private final String officialSiteUrl;

    @Schema(description = "관련 상품 코드(CD Japan). 없으면 빈 목록(섹션 숨김)", requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<String> productCodes;

    @Schema(description = "내가 스크랩한 공연인지. 비로그인 조회면 항상 false", requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean scrapped;

    @Schema(description = "선예매 알림을 켰는지. 기본 false, 비로그인 조회면 항상 false", requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean presaleAlarm;

    @Schema(description = "일반예매 알림을 켰는지. 기본 false, 비로그인 조회면 항상 false", requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean generalSaleAlarm;

    /**
     * @param today D-day 계산 기준일. "지금"을 이 클래스 안에서 정하지 않고 호출한 쪽(서비스)이 넘기게 해서,
     *              테스트에서 날짜를 고정해 검증할 수 있게 한다.
     * @param alarms 내가 켠 예매 알림 종류. 비로그인이면 빈 집합
     */
    public static ConcertUpcomingDetailDto from(Concert concert, LocalDate today, List<ConcertPresale> presales,
                                                List<ConcertGeneralSale> generalSales, boolean scrapped,
                                                Set<TicketAlarmType> alarms) {
        return ConcertUpcomingDetailDto.builder()
                .concertId(concert.getId())
                .artists(ConcertArtistDto.listOf(concert))
                .title(concert.getTitle())
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
                .lodgingUrl(concert.isLodgingVisible() ? concert.getLodgingUrl() : null)
                .officialSiteUrl(concert.getOfficialSiteUrl())
                .productCodes(List.copyOf(concert.getProductCodes()))
                .scrapped(scrapped)
                .presaleAlarm(alarms.contains(TicketAlarmType.PRESALE))
                .generalSaleAlarm(alarms.contains(TicketAlarmType.GENERAL_SALE))
                .build();
    }
}