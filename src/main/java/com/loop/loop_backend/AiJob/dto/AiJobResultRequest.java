package com.loop.loop_backend.AiJob.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.loop.loop_backend.AiJob.domain.AiJob;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 워커의 결과 보고. DONE이면 result, FAILED면 error가 있어야 한다.
 * 형식 규칙은 여기서 400으로 거르고, "받을지"(404·409)는 서비스·도메인이 정한다.
 */
@Schema(description = "결과 보고. DONE이면 result, FAILED면 error를 보낸다")
public record AiJobResultRequest(

        @NotBlank
        @Size(max = 100)
        @Schema(description = "워커 식별자", example = "pm-macbook", requiredMode = Schema.RequiredMode.REQUIRED)
        String workerId,

        @NotNull
        @Schema(description = "claim 때 받은 attempt 그대로", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer attempt,

        @NotNull
        @Schema(description = "DONE 또는 FAILED", example = "DONE", requiredMode = Schema.RequiredMode.REQUIRED)
        Outcome status,

        @Schema(description = "DONE일 때: AI 서버 API 응답의 data 그대로(JSON). result.status가 NOT_FOUND여도 DONE이다")
        JsonNode result,

        @Valid
        @Schema(description = "FAILED일 때: 실패 정보")
        Error error
) {

    /** 워커가 보고할 수 있는 상태. PENDING·RUNNING은 보고 대상이 아니라 받지 않는다. */
    public enum Outcome {
        DONE, FAILED
    }

    @Schema(description = "실패 정보")
    public record Error(

            @NotBlank
            @Size(max = 50)
            @Schema(description = "실패 코드", example = "LLM_TIMEOUT", requiredMode = Schema.RequiredMode.REQUIRED)
            String code,

            // 길이 제한으로 400을 주면 보고 자체가 실패하므로 제한하지 않고, 저장할 때 자른다
            @NotBlank
            @Schema(description = "관리자에게 보여 줄 한국어 안내 문구", example = "AI 분석 시간이 초과됐습니다.",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String message,

            @NotNull
            @Schema(description = "다시 시도할 만한 실패인지", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
            Boolean retryable
    ) {
    }

    @JsonIgnore
    @AssertTrue(message = "DONE이면 result가 있어야 합니다.")
    public boolean isResultPresentWhenDone() {
        return status != Outcome.DONE || (result != null && !result.isNull());
    }

    @JsonIgnore
    @AssertTrue(message = "FAILED면 error가 있어야 합니다.")
    public boolean isErrorPresentWhenFailed() {
        return status != Outcome.FAILED || error != null;
    }

    /** LEASE_EXPIRED는 Backend만 붙이는 코드다. 워커가 보내면 "늦게 온 결과로 덮어써도 되는 실패"와 구분할 수 없다. */
    @JsonIgnore
    @AssertTrue(message = "LEASE_EXPIRED는 워커가 보고할 수 없는 코드입니다.")
    public boolean isNotReportingLeaseExpired() {
        return error == null || !AiJob.LEASE_EXPIRED.equals(error.code());
    }
}