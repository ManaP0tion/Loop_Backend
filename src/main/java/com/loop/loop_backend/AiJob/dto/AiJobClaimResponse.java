package com.loop.loop_backend.AiJob.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.loop.loop_backend.AiJob.domain.AiJobType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "워커에 넘기는 작업")
public record AiJobClaimResponse(

        @Schema(description = "작업 ID", example = "123")
        Long jobId,

        @Schema(description = "작업 종류", example = "CONCERT_ENRICH")
        AiJobType type,

        @Schema(description = "AI 서버 API 요청 본문 그대로(JSON)")
        JsonNode payload,

        @Schema(description = "이번 시도 번호(첫 claim이 1). 결과 보고 때 그대로 돌려보낸다", example = "1")
        int attempt
) {
}