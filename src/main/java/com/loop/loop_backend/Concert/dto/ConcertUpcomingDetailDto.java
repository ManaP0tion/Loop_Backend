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
// price는 이 응답에 넣지 않기로 했다. showtime(공연 시간 안내)은 KOPIS 원문 텍스트 그대로 내려준다.
//
// null 표기는 OpenAPI 3.0의 nullable=true 대신 3.1 방식(types에 "null" 포함)을 쓴다.
// nullable=true는 스펙엔 들어가지만 스웨거 UI가 타입 옆에 안 보여줘서, type 배열(예: {"string","null"})로
// 명확히 보이게 한다.
//
// ※ 이 스펙은 "승인 시 필수값 검증"을 전제로 한 최종 형태다 (presaleDate/generalSaleDate/ticketVendors만
// 제외하고 전부 non-null) - 그 검증 기능은 아직 ConcertImportService.approve()에 없다. 지금 실제 응답은
// KOPIS 조회 실패, 어드민 미입력, 아티스트 미배정 등으로 이 필드들이 null일 수 있다. 검증 기능이 배포되기
// 전까지는 스펙과 실제 응답이 다를 수 있다는 뜻 - 프론트 선개발을 위해 최종 계약을 먼저 문서화한 것.
@Getter
@Builder
public class ConcertUpcomingDetailDto {

    @Schema(description = "콘서트 PK", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long concertId;

    @Schema(description = "아티스트 PK", types = {"integer"})
    private final Long artistId;

    @Schema(description = "아티스트명", types = {"string"})
    private final String artistName;

    @Schema(description = "공연명", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String title;

    @Schema(description = "포스터 이미지 URL")
    private final String posterUrl;

    @Schema(description = "공연장 이름")
    private final String venue;

    @Schema(description = "공연 시작일")
    private final LocalDate startDate;

    @Schema(description = "공연 종료일")
    private final LocalDate endDate;

    // 필드명이 그대로 JSON 키(dday)가 되도록 dDay가 아니라 dday로 둔다.
    // (Lombok getDDay() -> Jackson이 "dday"로 직렬화 - 필드명이 dDay면 이 이름 불일치 때문에
    //  swagger가 이 필드의 @Schema를 못 찾아서 required/nullable이 스펙에 안 실렸다)
    @Schema(description = "오늘(KST) 기준 startDate까지 남은 일수. 당일 0, 이미 시작한 공연은 음수")
    private final Integer dday;

    @Schema(description = "공연 시간 안내. KOPIS(dtguidance) 원문 텍스트 그대로다. 예: \"토요일(17:00), 일요일(16:00)\". " +
            "요일별 시각을 구조화하지 않았으니 프론트가 문장 그대로 보여준다.")
    private final String showtime;

    // 예매정보
    @Schema(description = "선예매 유무")
    private final Boolean presaleAvailable;

    @Schema(description = "선예매 시작일. 선예매가 없는 공연은 null", types = {"string", "null"})
    private final LocalDate presaleDate;

    @Schema(description = "일반 예매 시작일. 아직 미정인 공연은 null", types = {"string", "null"})
    private final LocalDate generalSaleDate;

    @Schema(description = "예매처 목록(이름+링크), 복수 가능. 예매처가 정해지지 않은 공연은 null",
            types = {"array", "null"})
    private final List<TicketVendorInfo> ticketVendors;

    // 공연장 정보
    @Schema(description = "공연장 주소")
    private final String venueAddress;

    @Schema(description = "이 공연이 열리는 홀의 수용 인원")
    private final Integer venueCapacity;

    @Schema(description = "공연장 위도 - 지도 링크는 프론트에서 좌표로 생성한다")
    private final Double venueLatitude;

    @Schema(description = "공연장 경도")
    private final Double venueLongitude;

    @Schema(description = "자리 배치도 이미지 URL(관리자 업로드)")
    private final String seatingChartImageUrl;

    @Schema(description = "내가 스크랩한 공연인지. 비로그인 조회면 항상 false", requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean scrapped;

    /**
     * @param today D-day 계산 기준일. "지금"을 이 클래스 안에서 정하지 않고 호출한 쪽(서비스)이 넘기게 해서,
     *              테스트에서 날짜를 고정해 검증할 수 있게 한다.
     */
    public static ConcertUpcomingDetailDto from(Concert concert, LocalDate today, boolean scrapped) {
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
                .ticketVendors(concert.getTicketVendors())
                .venueAddress(concert.getVenueAddress())
                .venueCapacity(concert.getVenueCapacity())
                .venueLatitude(concert.getVenueLatitude())
                .venueLongitude(concert.getVenueLongitude())
                .scrapped(scrapped)
                // presaleAvailable/presaleDate/generalSaleDate/seatingChartImageUrl: 어드민 입력 UI와
                // "승인 시 필수값 검증"이 아직 없어서 지금은 항상 null. 클래스 상단 주석 참고.
                .build();
    }
}