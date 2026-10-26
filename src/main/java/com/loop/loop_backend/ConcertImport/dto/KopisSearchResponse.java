package com.loop.loop_backend.ConcertImport.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "KOPIS 공연명 검색 결과")
public record KopisSearchResponse(

        @Schema(description = "검색 결과. 시작일 최신순(시작일 없는 공연은 맨 뒤)", requiredMode = Schema.RequiredMode.REQUIRED)
        List<KopisSearchItem> items,

        @Schema(description = "결과가 최대 건수(200)에 걸려 잘렸을 수 있으면 true. 검색어를 더 구체적으로 입력하도록 안내한다",
                example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean truncated) {
}