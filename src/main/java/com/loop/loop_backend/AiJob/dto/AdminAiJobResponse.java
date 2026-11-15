package com.loop.loop_backend.AiJob.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.loop.loop_backend.AiJob.domain.AiJob;
import com.loop.loop_backend.AiJob.domain.AiJobStatus;
import com.loop.loop_backend.AiJob.domain.AiJobType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 어드민 상태 조회. 화면은 2~3초마다 조회하고, createdAt 기준 2분 넘게 PENDING이면 "AI 워커가 꺼져 있을 수 있음"을 보여 준다.
 * result는 DB에 문자열로 저장된 JSON이라 파싱은 서비스가 하고 여기에는 JsonNode로 받는다.
 */
@Schema(description = "AI 작업 상태")
public record AdminAiJobResponse(

        @Schema(description = "작업 ID", example = "123")
        Long jobId,

        @Schema(description = "작업 종류", example = "CONCERT_ENRICH")
        AiJobType type,

        @Schema(description = "대상 공연 PK", example = "31")
        Long concertId,

        @Schema(description = "PENDING(대기) / RUNNING(처리 중) / DONE(결과 있음) / FAILED(실패)", example = "DONE")
        AiJobStatus status,

        @Schema(description = "DONE일 때: AI 결과(JSON 그대로). 화면을 미리 채우는 데 쓰고, 저장은 관리자 확인 후 기존 API로 한다",
                types = {"object", "null"})
        JsonNode result,

        @Schema(description = "FAILED일 때: 실패 정보", types = {"object", "null"})
        Error error,

        @Schema(description = "시도 횟수(0이면 아직 워커가 가져가지 않음)", example = "1")
        int attempt,

        @Schema(description = "작업을 만든 시각")
        LocalDateTime createdAt,

        @Schema(description = "끝난 시각(DONE·FAILED)", types = {"string", "null"})
        LocalDateTime finishedAt
) {

    @Schema(description = "실패 정보")
    public record Error(
            @Schema(description = "실패 코드. LEASE_EXPIRED면 워커(PM PC)가 켜져 있는지 확인", example = "LLM_TIMEOUT")
            String code,

            @Schema(description = "관리자에게 보여 줄 안내 문구", example = "AI 분석 시간이 초과됐습니다.")
            String message,

            @Schema(description = "true면 \"다시 시도\" 버튼을 보여 새 작업을 만든다", example = "true")
            boolean retryable
    ) {
    }

    public static AdminAiJobResponse of(AiJob job, JsonNode result) {
        Error error = job.getErrorCode() == null ? null
                : new Error(job.getErrorCode(), job.getErrorMessage(), Boolean.TRUE.equals(job.getErrorRetryable()));
        return new AdminAiJobResponse(
                job.getId(), job.getType(), job.getConcertId(), job.getStatus(),
                result, error, job.getAttempt(), job.getCreatedAt(), job.getFinishedAt());
    }
}