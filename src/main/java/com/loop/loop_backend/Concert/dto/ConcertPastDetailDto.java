package com.loop.loop_backend.Concert.dto;

import com.loop.loop_backend.Concert.domain.Concert;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

// 지난 공연 상세. 예매/공연장 상세 정보는 이제 와서 의미가 없어 제외 — 공연명/아티스트/공연장/날짜/공연 시간 안내만.
// 추후 셋리스트 기능이 붙으면 이 DTO에 필드가 추가될 예정.
@Getter
@Builder
public class ConcertPastDetailDto {

    @Schema(description = "콘서트 PK", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long concertId;

    @Schema(description = "공연명", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String title;

    @Schema(description = "아티스트명. 아티스트 없는 공연(페스티벌 등)은 null", nullable = true)
    private final String artistName;

    @Schema(description = "공연장 이름. 날짜 미정 공연처럼 정보가 없을 수 있어 null 가능", nullable = true)
    private final String venue;

    @Schema(description = "공연 시작일. 날짜 미정 공연은 null", nullable = true)
    private final LocalDate startDate;

    @Schema(description = "공연 종료일. 날짜 미정 공연은 null", nullable = true)
    private final LocalDate endDate;

    @Schema(description = "공연 시간 안내. KOPIS(dtguidance) 원문 텍스트 그대로다. 예: \"토요일(17:00), 일요일(16:00)\". " +
            "못 가져왔거나 이 정보를 저장하기 전에 승인된 공연은 null", nullable = true)
    private final String showtime;

    public static ConcertPastDetailDto from(Concert concert) {
        return ConcertPastDetailDto.builder()
                .concertId(concert.getId())
                .title(concert.getTitle())
                .artistName(concert.getArtist() != null ? concert.getArtist().getName() : null)
                .venue(concert.getVenue())
                .startDate(concert.getStartDate())
                .endDate(concert.getEndDate())
                .showtime(concert.getShowtime())
                .build();
    }
}