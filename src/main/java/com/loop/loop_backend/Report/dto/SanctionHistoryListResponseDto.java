package com.loop.loop_backend.Report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.List;

@Getter
@Schema(description = "내 제재 이력 응답 DTO")
public class SanctionHistoryListResponseDto {

    @Schema(description = "제재를 받은 적이 있는지 여부 (프론트 안내 문구 표시 여부 판단용)", example = "true")
    private final boolean hasSanctionHistory;

    @Schema(description = "제재 이력 목록 (최신순)")
    private final List<SanctionHistoryResponseDto> history;

    public SanctionHistoryListResponseDto(List<SanctionHistoryResponseDto> history) {
        this.hasSanctionHistory = !history.isEmpty();
        this.history = history;
    }
}