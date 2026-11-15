package com.loop.loop_backend.Concert.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "동행 모집 공연 목록")
public record CompanionConcertListDto(
        @Schema(description = "지금 동행 모집 중(OPEN)인 공연 전체 수. 마감(CLOSED)은 세지 않고 size와도 상관없음", example = "7",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long totalCount,

        @Schema(description = "모집 중(OPEN)이 위, 마감(CLOSED)이 아래. 각각 공연일 가까운 순(날짜 미정은 맨 뒤), size만큼",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<CompanionConcertDto> concerts
) {
}
