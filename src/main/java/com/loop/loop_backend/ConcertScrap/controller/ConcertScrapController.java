package com.loop.loop_backend.ConcertScrap.controller;

import com.loop.loop_backend.Concert.dto.ConcertPeriod;
import com.loop.loop_backend.Concert.dto.ConcertSummaryDto;
import com.loop.loop_backend.ConcertScrap.service.ConcertScrapService;
import com.loop.loop_backend.common.exception.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// /api/concerts/** 아래에 두지 않은 이유: 그 경로는 GET이 permitAll, POST/DELETE가 ADMIN 전용이라
// 로그인 필수 + 일반 유저 쓰기가 필요한 스크랩과 맞지 않는다. /api/users/me/** 는 인증 필수로 걸려 있다.
@RestController
@RequestMapping("/api/users/me/scraps")
@RequiredArgsConstructor
@Tag(name = "ConcertScrap", description = "콘서트 스크랩 API (로그인 필수)")
public class ConcertScrapController {

    private final ConcertScrapService concertScrapService;

    @PostMapping("/{concertId}")
    @Operation(summary = "콘서트 스크랩", description = "콘서트를 스크랩합니다. 이미 스크랩한 상태면 그대로 성공 처리됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 성공"),
            @ApiResponse(responseCode = "404", description = "콘서트 없음",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"콘서트를 찾을 수 없습니다.\",\"code\":404}")))
    })
    public ResponseEntity<CommonResponse<Void>> scrap(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "콘서트 PK") @PathVariable Long concertId) {
        concertScrapService.scrap(userId, concertId);
        return ResponseEntity.ok(CommonResponse.success("스크랩했습니다.", null));
    }

    @DeleteMapping("/{concertId}")
    @Operation(summary = "콘서트 스크랩 취소", description = "콘서트 스크랩을 취소합니다. 스크랩하지 않은 상태여도 그대로 성공 처리됩니다.")
    @ApiResponse(responseCode = "200", description = "처리 성공")
    public ResponseEntity<CommonResponse<Void>> unscrap(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "콘서트 PK") @PathVariable Long concertId) {
        concertScrapService.unscrap(userId, concertId);
        return ResponseEntity.ok(CommonResponse.success("스크랩을 취소했습니다.", null));
    }

    @GetMapping
    @Operation(summary = "내 스크랩 목록 조회",
            description = "내가 스크랩한 콘서트를 period(예정/지난)별로 조회합니다. 예정/지난 판단과 정렬은 공연 목록 조회와 동일합니다 — " +
                    "예정 공연은 가까운 날짜순(날짜 미정은 예정의 맨 뒤), 지난 공연은 최근 종료순.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<CommonResponse<List<ConcertSummaryDto>>> getMyScraps(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "조회 시점 (UPCOMING 예정 / PAST 지난)") @RequestParam ConcertPeriod period) {
        return ResponseEntity.ok(CommonResponse.success(concertScrapService.getMyScraps(userId, period)));
    }
}