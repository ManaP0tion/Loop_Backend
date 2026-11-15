package com.loop.loop_backend.CompanionHeart.dto;

import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.CompanionPost.dto.CompanionResponseDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Getter
@Schema(description = "하트 탭 상세화면 - 콘서트 내 관람일별 하트 프로필 DTO")
public class HeartedConcertDetailDto {

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

    @Schema(description = "관람 일차별 하트한 동행 프로필 목록")
    private final Map<WatchDay, List<CompanionResponseDto>> days;

    public HeartedConcertDetailDto(Long concertId, String concertTitle, Long artistId, String artistName,
                                    LocalDate startDate, LocalDate endDate,
                                    Map<WatchDay, List<CompanionResponseDto>> days) {
        this.concertId = concertId;
        this.concertTitle = concertTitle;
        this.artistId = artistId;
        this.artistName = artistName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.days = days;
    }
}