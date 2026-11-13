package com.loop.loop_backend.Concert.dto;

import com.loop.loop_backend.Concert.domain.CompanionStatus;
import com.loop.loop_backend.Concert.domain.Concert;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "동행 모집 공연 카드")
public record CompanionConcertDto(
        @Schema(description = "공연 PK", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
        Long concertId,

        @Schema(description = "공연명", example = "SUMMER SONIC 2026", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @Schema(description = "포스터 이미지 URL", types = {"string", "null"})
        String posterUrl,

        @Schema(description = "공연 시작일. 날짜 미정이면 null", types = {"string", "null"})
        LocalDate startDate,

        @Schema(description = "공연 종료일 (하루 공연은 시작일과 같다). 날짜 미정이면 null", types = {"string", "null"})
        LocalDate endDate,

        @Schema(description = "동행 상태. OPEN 모집 중 / CLOSED 마감(비공개 전환된 공연 포함). 이 목록에 NOT_OPENED는 오지 않는다",
                example = "OPEN", requiredMode = Schema.RequiredMode.REQUIRED)
        CompanionStatus companionStatus,

        @Schema(description = "이 공연에 관람일이 지나지 않은 내 동행 프로필이 하나라도 있는지(DAY 무관, 비공개 프로필 포함). " +
                "비로그인이면 null", types = {"boolean", "null"})
        Boolean myProfileRegistered
) {

    public static CompanionConcertDto of(Concert concert, Boolean myProfileRegistered) {
        return new CompanionConcertDto(concert.getId(), concert.getTitle(), concert.getPosterUrl(),
                concert.getStartDate(), concert.getEndDate(), concert.companionStatusForUsers(), myProfileRegistered);
    }
}
