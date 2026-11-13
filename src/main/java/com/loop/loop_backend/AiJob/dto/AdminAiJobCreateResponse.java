package com.loop.loop_backend.AiJob.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 작업 만들기 결과. 같은 공연·종류의 작업이 이미 진행 중이면 그 작업의 ID")
public record AdminAiJobCreateResponse(

        @Schema(description = "작업 ID. 이 값으로 상태를 조회한다", example = "123")
        Long jobId
) {
}