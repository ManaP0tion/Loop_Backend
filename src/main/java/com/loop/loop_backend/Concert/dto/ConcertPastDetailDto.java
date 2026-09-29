package com.loop.loop_backend.Concert.dto;

import com.loop.loop_backend.Concert.domain.Concert;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

// 지난 공연 상세. 예매/공연장 상세 정보는 이제 와서 의미가 없어 제외 — 공연명/아티스트/공연장/날짜/공연 시간 안내만.
// 추후 셋리스트 기능이 붙으면 이 DTO에 필드가 추가될 예정.
//
// ※ ConcertUpcomingDetailDto와 같은 정책: "승인 시 필수값 검증"을 전제로 한 최종 계약이라 전부 non-null이다.
// 그 검증 기능은 아직 ConcertImportService.approve()에 없어서, 지금 실제 응답은 아티스트 미배정·KOPIS 조회
// 실패·날짜 미정 등으로 이 필드들이 null일 수 있다.
@Getter
@Builder
public class ConcertPastDetailDto {

    @Schema(description = "콘서트 PK", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long concertId;

    @Schema(description = "공연명", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String title;

    @Schema(description = "아티스트명")
    private final String artistName;

    @Schema(description = "공연장 이름")
    private final String venue;

    @Schema(description = "공연 시작일")
    private final LocalDate startDate;

    @Schema(description = "공연 종료일")
    private final LocalDate endDate;

    @Schema(description = "공연 시간 안내. KOPIS(dtguidance) 원문 텍스트 그대로다. 예: \"토요일(17:00), 일요일(16:00)\"")
    private final String showtime;

    @Schema(description = "내가 스크랩한 공연인지. 비로그인 조회면 항상 false", requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean scrapped;

    public static ConcertPastDetailDto from(Concert concert, boolean scrapped) {
        return ConcertPastDetailDto.builder()
                .concertId(concert.getId())
                .title(concert.getTitle())
                .artistName(concert.getArtist() != null ? concert.getArtist().getName() : null)
                .venue(concert.getVenue())
                .startDate(concert.getStartDate())
                .endDate(concert.getEndDate())
                .showtime(concert.getShowtime())
                .scrapped(scrapped)
                .build();
    }
}