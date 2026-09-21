package com.loop.loop_backend.Concert.dto;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

// 예정 공연 상세.
// 선예매 여부/날짜, 일반 예매 날짜, 자리 배치도는 어드민에서 수동 입력할 예정인데 아직 입력 기능과
// Concert 컬럼이 없어서, 지금은 응답에 필드만 있고 항상 null이다. 입력 기능이 생기면 from()에서 채운다.
// price는 이 응답에 넣지 않기로 했다. showtime(공연 시간 안내)은 KOPIS 원문 텍스트 그대로 내려준다.
@Getter
@Builder
public class ConcertUpcomingDetailDto {

    private final Long concertId;
    private final Long artistId;
    private final String artistName;
    private final String title;
    private final String posterUrl;
    private final String venue; // 공연장 이름
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final Integer dDay; // 오늘(KST) 기준 startDate까지 남은 일수. startDate 없으면 null
    // 공연 시간 안내. KOPIS(dtguidance)가 주는 자유 텍스트 그대로다. 예: "토요일(17:00), 일요일(16:00)"
    // 요일별 시각을 구조화하지 않았으니 프론트가 문장 그대로 보여준다. 못 가져왔거나 예전에 승인된 공연은 null일 수 있다.
    private final String showtime;

    // 예매정보
    private final Boolean presaleAvailable; // 선예매 유무
    private final LocalDate presaleDate; // 선예매 시작일
    private final LocalDate generalSaleDate; // 일반 예매 시작일
    private final List<TicketVendorInfo> ticketVendors; // 예매처 목록(이름+링크), 복수 가능

    // 공연장 정보
    private final String venueAddress; // 공연장 주소
    private final Integer venueCapacity; // 이 공연이 열리는 홀의 수용 인원
    private final Double venueLatitude; // 공연장 위도 - 지도 링크는 프론트에서 좌표로 생성
    private final Double venueLongitude; // 공연장 경도
    private final String seatingChartImageUrl; // 자리 배치도 이미지 URL (관리자 업로드)

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
                .dDay(concert.getStartDate() != null
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