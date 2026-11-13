package com.loop.loop_backend.AiJob.dto;

import com.loop.loop_backend.AiJob.domain.AiJobType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 어드민 "AI로 찾기". payload는 Backend가 DB에서 만든다 - 어드민은 종류와 공연만 보낸다. */
@Schema(description = "AI 작업 만들기 요청")
public record AdminAiJobCreateRequest(

        @NotNull
        @Schema(description = "작업 종류. CONCERT_ENRICH(티켓 정보), FESTIVAL_LINEUP(페스티벌 라인업). SETLIST_ACTUAL은 아직 지원하지 않는다",
                example = "CONCERT_ENRICH", requiredMode = Schema.RequiredMode.REQUIRED)
        AiJobType type,

        @NotNull
        @Schema(description = "대상 공연 PK", example = "31", requiredMode = Schema.RequiredMode.REQUIRED)
        Long concertId
) {
}