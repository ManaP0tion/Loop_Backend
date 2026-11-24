package com.loop.loop_backend.AiJob.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 워커의 "할 일 있어?" 요청.
 * types는 enum이 아닌 문자열로 받는다 - 워커가 새 종류를 먼저 배포해 Backend가 모르는 값이 섞여도
 * claim이 400으로 실패하지 않고, 아는 종류 중에서만 작업을 주기 위해서다.
 */
@Schema(description = "다음 작업 가져오기 요청")
public record AiJobClaimRequest(

        @NotBlank
        @Size(max = 100)
        @Schema(description = "워커 식별자", example = "pm-macbook", requiredMode = Schema.RequiredMode.REQUIRED)
        String workerId,

        @NotNull
        @Schema(description = "이 워커가 처리할 수 있는 작업 종류. 이 중에서만 준다. Backend가 모르는 값은 무시한다",
                example = "[\"CONCERT_ENRICH\", \"SETLIST_ACTUAL\", \"FESTIVAL_LINEUP\"]",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> types
) {
}