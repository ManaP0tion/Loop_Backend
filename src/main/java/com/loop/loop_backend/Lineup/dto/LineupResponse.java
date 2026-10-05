package com.loop.loop_backend.Lineup.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * 페스티벌 라인업(NO.41·42). 한 번에 전부 내려주고 DAY 탭은 프론트가 day 값으로 나눈다.
 * 헤드라이너 미리보기는 artists에서 headliner=true를 순서대로 고른다(3팀 미만이면 숨김, 초과면 앞 3팀).
 */
@Schema(description = "페스티벌 라인업. 라인업이 아직 없거나 페스티벌이 아니면 artists가 빈 목록")
public record LineupResponse(

        @Schema(description = "공연 기간의 DAY 목록(순서대로). 1개면 DAY 탭 없이 목록만 보여준다. 기간이 없거나 페스티벌이 아니면 빈 목록",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<Day> days,

        @Schema(description = "라인업 전체. 관리자가 정한 노출 순서대로(전체 탭에 그대로 사용)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<LineupItemResponse> artists
) {

    @Schema(description = "DAY")
    public record Day(
            @Schema(description = "DAY 번호(1부터)", example = "1", requiredMode = Schema.RequiredMode.REQUIRED) int day,
            @Schema(description = "날짜", example = "2026-11-20", requiredMode = Schema.RequiredMode.REQUIRED) LocalDate date) {
    }
}
