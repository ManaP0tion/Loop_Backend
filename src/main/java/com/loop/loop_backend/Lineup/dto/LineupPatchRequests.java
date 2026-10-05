package com.loop.loop_backend.Lineup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 라인업 한 항목을 바꾸는 작은 요청들. 각각 즉시 저장된다. */
public final class LineupPatchRequests {

    private LineupPatchRequests() {
    }

    @Schema(description = "DAY 변경 요청")
    public record Day(
            @NotNull
            @Schema(description = "새 DAY 번호(1부터)", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
            Integer day) {
    }

    @Schema(description = "순서 한 칸 이동 요청. 맨 앞에서 UP, 맨 뒤에서 DOWN은 변화 없음")
    public record Order(
            @NotNull
            @Schema(description = "이동 방향", example = "UP", requiredMode = Schema.RequiredMode.REQUIRED)
            Direction direction) {
    }

    @Schema(description = "헤드라이너 지정/해제 요청")
    public record Headliner(
            @NotNull
            @Schema(description = "헤드라이너 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
            Boolean headliner) {
    }

    public enum Direction { UP, DOWN }
}
