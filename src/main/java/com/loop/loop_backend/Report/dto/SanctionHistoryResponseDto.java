package com.loop.loop_backend.Report.dto;

import com.loop.loop_backend.Report.domain.SanctionRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Schema(description = "내 제재 이력 응답 DTO")
public class SanctionHistoryResponseDto {

    @Schema(description = "정지 해제 예정일시 (null이면 영구정지)")
    private final LocalDateTime suspendedUntil;

    @Schema(description = "관리자 처리 사유", example = "반복적인 이용 규칙 위반으로 인한 정지")
    private final String adminNote;

    @Schema(description = "조치 처리일시")
    private final LocalDateTime processedAt;

    public SanctionHistoryResponseDto(SanctionRecord record) {
        this.suspendedUntil = record.getSuspendedUntil();
        this.adminNote = record.getAdminNote();
        this.processedAt = record.getProcessedAt();
    }
}