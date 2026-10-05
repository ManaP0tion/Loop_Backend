package com.loop.loop_backend.Lineup.controller;

import com.loop.loop_backend.Lineup.dto.LineupResponse;
import com.loop.loop_backend.Lineup.service.LineupService;
import com.loop.loop_backend.common.exception.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 페스티벌 라인업 조회(BE-14). 비로그인 열람 - GET /api/concerts/** 는 SecurityConfig에서 permitAll. */
@RestController
@RequestMapping("/api/concerts/{concertId}/lineup")
@RequiredArgsConstructor
@Tag(name = "Lineup", description = "페스티벌 라인업 조회 (비로그인 가능)")
public class LineupController {

    private final LineupService lineupService;

    @GetMapping
    @Operation(summary = "페스티벌 라인업 조회",
            description = "DAY 목록과 라인업 전체를 한 번에 내려준다. 전체 탭은 artists 순서 그대로, DAY 탭은 day 값으로 프론트에서 나눈다.\n\n" +
                    "- days가 1개(하루 페스티벌)면 DAY 탭 없이 목록만\n" +
                    "- 헤드라이너 미리보기: artists에서 headliner=true를 순서대로 - 3팀 미만이면 숨김, 초과면 앞 3팀\n" +
                    "- 라인업이 아직 없으면 artists 빈 목록(200), 페스티벌이 아니면 days·artists 모두 빈 목록\n" +
                    "- 공연이 끝난 뒤에도 같은 응답\n\n" +
                    "비공개(오픈 예정) 공연이면 403.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "오픈 예정(비공개) 공연 - CONCERT_NOT_OPEN"),
            @ApiResponse(responseCode = "404", description = "콘서트 없음")
    })
    public ResponseEntity<CommonResponse<LineupResponse>> getLineup(
            @Parameter(description = "콘서트 PK") @PathVariable Long concertId) {
        return ResponseEntity.ok(CommonResponse.success(lineupService.publicLineup(concertId)));
    }
}
