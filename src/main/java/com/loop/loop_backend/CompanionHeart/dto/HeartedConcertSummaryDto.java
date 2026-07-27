package com.loop.loop_backend.CompanionHeart.dto;

import com.loop.loop_backend.CompanionPost.dto.CompanionResponseDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Schema(description = "하트 탭 메인화면 - 콘서트별 하트 요약 DTO")
public class HeartedConcertSummaryDto {

    @Schema(description = "콘서트 PK", example = "1")
    private final Long concertId;

    @Schema(description = "콘서트 제목", example = "요네즈 켄시 콘서트")
    private final String concertTitle;

    @Schema(description = "아티스트 PK", example = "5")
    private final Long artistId;

    @Schema(description = "아티스트 이름", example = "요네즈 켄시")
    private final String artistName;

    @Schema(description = "콘서트 시작일")
    private final LocalDate startDate;

    @Schema(description = "콘서트 종료일")
    private final LocalDate endDate;

    @Schema(description = "공연 장소", example = "잠실종합운동장")
    private final String venue;

    @Schema(description = "이 콘서트에서 내가 하트한 프로필 총 개수", example = "5")
    private final long heartCount;

    @Schema(description = "최근 하트한 순 미리보기 (최대 2개, 목록 조회와 동일한 동행 프로필 정보)")
    private final List<CompanionResponseDto> companions;

    public HeartedConcertSummaryDto(Long concertId, String concertTitle, Long artistId, String artistName,
                                     LocalDate startDate, LocalDate endDate, String venue,
                                     long heartCount, List<CompanionResponseDto> companions) {
        this.concertId = concertId;
        this.concertTitle = concertTitle;
        this.artistId = artistId;
        this.artistName = artistName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.venue = venue;
        this.heartCount = heartCount;
        this.companions = companions;
    }
}