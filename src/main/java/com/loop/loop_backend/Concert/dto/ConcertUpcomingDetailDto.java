package com.loop.loop_backend.Concert.dto;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

// 예정 공연 상세.
// 선예매 여부/날짜, 일반 예매 날짜, 자리 배치도는 어드민에서 수동 입력할 예정인데 아직 입력 기능과
// Concert 컬럼이 없어서, 지금은 응답에 필드만 있고 항상 null이다. 입력 기능이 생기면 from()에서 채운다.
// price는 이 응답에 넣지 않기로 했다. showtime(공연 시간 안내)은 KOPIS 원문 텍스트 그대로 내려준다.
//
// nullable 기준: concertId/title 말고는 DB 컬럼 자체가 nullable이라 전부 null 가능하다.
// - artistId/artistName: 아티스트 없는 공연(페스티벌 등)은 원래 null
// - venue/startDate/endDate: "날짜 미정 공연"이 존재해서 null 가능
// - showtime/ticketVendors/venue*: 승인 시 KOPIS 조회 실패, 또는 이 기능 이전에 승인된 공연이면 null
// - presaleAvailable/presaleDate/generalSaleDate/seatingChartImageUrl: 어드민 입력 기능이 생긴 뒤에도
//   선예매가 없는 공연, 아직 관리자가 안 채운 공연이 있을 수 있어 계속 null 가능 - "언젠가 다 채워짐"이 아니다
@Getter
@Builder
public class ConcertUpcomingDetailDto {

    @Schema(description = "콘서트 PK", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long concertId;

    @Schema(description = "아티스트 PK. 아티스트 없는 공연(페스티벌 등)은 null", nullable = true)
    private final Long artistId;

    @Schema(description = "아티스트명. 아티스트 없는 공연(페스티벌 등)은 null", nullable = true)
    private final String artistName;

    @Schema(description = "공연명", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String title;

    @Schema(description = "포스터 이미지 URL", nullable = true)
    private final String posterUrl;

    @Schema(description = "공연장 이름. 날짜 미정 공연처럼 정보가 없을 수 있어 null 가능", nullable = true)
    private final String venue;

    @Schema(description = "공연 시작일. 날짜 미정 공연은 null", nullable = true)
    private final LocalDate startDate;

    @Schema(description = "공연 종료일. 날짜 미정 공연은 null", nullable = true)
    private final LocalDate endDate;

    // 필드명이 그대로 JSON 키(dday)가 되도록 dDay가 아니라 dday로 둔다.
    // (Lombok getDDay() -> Jackson이 "dday"로 직렬화 - 필드명이 dDay면 이 이름 불일치 때문에
    //  swagger가 이 필드의 @Schema를 못 찾아서 required/nullable이 스펙에 안 실렸다)
    @Schema(description = "오늘(KST) 기준 startDate까지 남은 일수. 당일 0, 이미 시작한 공연은 음수. " +
            "startDate 없으면 null", nullable = true)
    private final Integer dday;

    @Schema(description = "공연 시간 안내. KOPIS(dtguidance) 원문 텍스트 그대로다. 예: \"토요일(17:00), 일요일(16:00)\". " +
            "요일별 시각을 구조화하지 않았으니 프론트가 문장 그대로 보여준다. " +
            "못 가져왔거나 이 기능 이전에 승인된 공연은 null", nullable = true)
    private final String showtime;

    // 예매정보
    @Schema(description = "선예매 유무. 어드민 수동 입력 기능이 생기기 전까지 항상 null", nullable = true)
    private final Boolean presaleAvailable;

    @Schema(description = "선예매 시작일. 어드민 수동 입력 기능이 생기기 전까지 항상 null", nullable = true)
    private final LocalDate presaleDate;

    @Schema(description = "일반 예매 시작일. 어드민 수동 입력 기능이 생기기 전까지 항상 null", nullable = true)
    private final LocalDate generalSaleDate;

    @Schema(description = "예매처 목록(이름+링크), 복수 가능. 승인 시 KOPIS에서 채워지며, " +
            "조회 실패나 이 기능 이전에 승인된 공연은 null", nullable = true)
    private final List<TicketVendorInfo> ticketVendors;

    // 공연장 정보
    @Schema(description = "공연장 주소. 승인 시 KOPIS 시설 조회로 채워지며, 실패하거나 이 기능 이전에 " +
            "승인된 공연은 null", nullable = true)
    private final String venueAddress;

    @Schema(description = "이 공연이 열리는 홀의 수용 인원. 위 venueAddress와 동일한 사유로 null 가능", nullable = true)
    private final Integer venueCapacity;

    @Schema(description = "공연장 위도 - 지도 링크는 프론트에서 좌표로 생성한다. 위 venueAddress와 동일한 사유로 null 가능",
            nullable = true)
    private final Double venueLatitude;

    @Schema(description = "공연장 경도. 위 venueAddress와 동일한 사유로 null 가능", nullable = true)
    private final Double venueLongitude;

    @Schema(description = "자리 배치도 이미지 URL(관리자 업로드). 어드민 수동 입력 기능이 생기기 전까지 항상 null",
            nullable = true)
    private final String seatingChartImageUrl;

    /**
     * @param today D-day 계산 기준일. "지금"을 이 클래스 안에서 정하지 않고 호출한 쪽(서비스)이 넘기게 해서,
     *              테스트에서 날짜를 고정해 검증할 수 있게 한다.
     */
    public static ConcertUpcomingDetailDto from(Concert concert, LocalDate today) {
        return ConcertUpcomingDetailDto.builder()
                .concertId(concert.getId())
                // 아티스트 없는 공연(페스티벌 등)은 아티스트 필드가 null
                .artistId(concert.getArtist() != null ? concert.getArtist().getId() : null)
                .artistName(concert.getArtist() != null ? concert.getArtist().getName() : null)
                .title(concert.getTitle())
                .posterUrl(concert.getPosterUrl())
                .venue(concert.getVenue())
                .startDate(concert.getStartDate())
                .endDate(concert.getEndDate())
                // 시작일이 없는 공연(날짜 미정)은 D-day를 계산할 수 없어 null.
                // 여러 날 공연이 이미 시작해 진행 중이면 음수가 나온다 - 표시 방식은 프론트가 정한다.
                .dday(concert.getStartDate() != null
                        ? (int) ChronoUnit.DAYS.between(today, concert.getStartDate())
                        : null)
                .showtime(concert.getShowtime())
                // 예매처 목록/공연장 정보는 승인 시 KOPIS에서 채워지며, 조회에 실패했거나 예전에 승인된 공연은 null
                .ticketVendors(concert.getTicketVendors())
                .venueAddress(concert.getVenueAddress())
                .venueCapacity(concert.getVenueCapacity())
                .venueLatitude(concert.getVenueLatitude())
                .venueLongitude(concert.getVenueLongitude())
                // presaleAvailable, presaleDate, generalSaleDate, seatingChartImageUrl은
                // 어드민 수동 입력 기능이 생기기 전까지 설정하지 않는다 (항상 null)
                .build();
    }
}