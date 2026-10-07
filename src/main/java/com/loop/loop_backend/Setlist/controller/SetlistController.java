package com.loop.loop_backend.Setlist.controller;

import com.loop.loop_backend.Setlist.dto.SetlistCandidatesResponse;
import com.loop.loop_backend.Setlist.dto.SetlistRankingResponse;
import com.loop.loop_backend.Setlist.dto.SetlistResponse;
import com.loop.loop_backend.Setlist.dto.SetlistResultResponse;
import com.loop.loop_backend.Setlist.service.SetlistResultService;
import com.loop.loop_backend.Setlist.service.SetlistService;
import com.loop.loop_backend.Setlist.service.SetlistVoteService;
import com.loop.loop_backend.common.exception.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 예상 셋리스트 조회(3-3·3-4). 비로그인 열람 - GET /api/concerts/** 는 SecurityConfig에서 permitAll. */
@RestController
@RequestMapping("/api/concerts/{concertId}/setlist")
@RequiredArgsConstructor
@Tag(name = "Setlist", description = "예상 셋리스트 조회 (비로그인 가능). 투표는 /api/users/me/setlist-votes")
public class SetlistController {

    private final SetlistVoteService voteService;
    private final SetlistService setlistService;
    private final SetlistResultService resultService;

    @GetMapping("/result")
    @Operation(summary = "예상 셋리스트 결과(공연 후)",
            description = "적중률 + 실제 셋리스트 + 예상했지만 나오지 않은 곡. 실제 셋리스트를 고치면 다음 조회부터 재산출된 값.\n\n" +
                    "- 적중률 = 맞힌 곡 수 ÷ 실제 셋리스트 곡 수(중복 연주 1회, 순서·앵코르 무시)\n" +
                    "- overall(팬 적중률) = 득표 상위 n곡 기준, averagePercent = 투표자 개인 적중률 평균\n" +
                    "- mine = 로그인 + 투표한 유저만. 없으면 null → 팬 적중률·참여자 수로 대체\n" +
                    "- songs: fanPredicted(배경) · mine(체크) · unexpected(아무도 예상하지 못한 곡)\n" +
                    "- missedSongs: 득표순. 투표 유저는 mine=true, 미투표·비로그인은 fanPredicted=true만 골라 쓴다\n" +
                    "- 실제 셋리스트 저장 전이면 ready=false(두 섹션 대기 문구)\n\n" +
                    "비공개(오픈 예정) 공연이면 403.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "오픈 예정(비공개) 공연 - CONCERT_NOT_OPEN"),
            @ApiResponse(responseCode = "404", description = "콘서트 없음")
    })
    public ResponseEntity<CommonResponse<SetlistResultResponse>> result(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "콘서트 PK") @PathVariable Long concertId) {
        return ResponseEntity.ok(CommonResponse.success(resultService.result(concertId, userId)));
    }

    @GetMapping("/ranking")
    @Operation(summary = "예상 셋리스트 순위",
            description = "득표순, 동점이면 곡 정렬 순번 순(공동 순위 없음). 득표한 곡 전체를 한 번에 준다.\n\n" +
                    "- 미리보기 = 앞 5곡, 전체 순위 = 20곡 단위 더보기(프론트)\n" +
                    "- highlighted = 상위 n곡(n = highlightCount)\n" +
                    "- 로그인 상태면 내가 고른 곡에 mine=true(투표 후 상태 화면)\n" +
                    "- votingClosed = 마감 여부. 마감 후에도 순위는 그대로 열람\n" +
                    "- 투표 0건이면 songs 빈 목록, 셋리스트를 운영하지 않는 공연이면 highlightCount null\n\n" +
                    "비공개(오픈 예정) 공연이면 403.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "오픈 예정(비공개) 공연 - CONCERT_NOT_OPEN"),
            @ApiResponse(responseCode = "404", description = "콘서트 없음")
    })
    public ResponseEntity<CommonResponse<SetlistRankingResponse>> ranking(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "콘서트 PK") @PathVariable Long concertId) {
        return ResponseEntity.ok(CommonResponse.success(voteService.ranking(concertId, userId)));
    }

    @GetMapping("/past")
    @Operation(summary = "지난 공연 셋리스트",
            description = "최근 공연(RECENT) → 지난 내한(PREVIOUS_VISIT) 순, 최대 2건.\n\n" +
                    "- 2건이면 칩 2개, 1건이면 칩 없이 목록만, 0건이면 섹션 미렌더링\n" +
                    "- 헤더: 투어명(null이면 생략) · 날짜 · 장소 · 곡 수. 앨범아트는 쓰지 않는다\n\n" +
                    "비공개(오픈 예정) 공연이면 403.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "오픈 예정(비공개) 공연 - CONCERT_NOT_OPEN"),
            @ApiResponse(responseCode = "404", description = "콘서트 없음")
    })
    public ResponseEntity<CommonResponse<List<SetlistResponse>>> past(
            @Parameter(description = "콘서트 PK") @PathVariable Long concertId) {
        return ResponseEntity.ok(CommonResponse.success(setlistService.pastSetlists(concertId)));
    }

    @GetMapping("/candidates")
    @Operation(summary = "예상 셋리스트 후보(곡 선택 화면)",
            description = "공연 아티스트의 곡 전체를 정렬 순번대로 준다. 순위·득표 수는 없다(상위권 쏠림 방지).\n\n" +
                    "- 50곡 → 20곡씩 더보기, 원제·로마자·한글 검색은 프론트가 이 목록으로 한다(로마자는 화면 미노출)\n" +
                    "- maxSelect = 최대 선택 곡 수 n, votingClosed = 마감(공연 시작일 00:00 KST) 여부\n" +
                    "- 셋리스트를 운영하지 않는 공연(페스티벌·예상 곡 수 없음)이면 maxSelect null, songs 빈 목록\n\n" +
                    "비공개(오픈 예정) 공연이면 403.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "오픈 예정(비공개) 공연 - CONCERT_NOT_OPEN"),
            @ApiResponse(responseCode = "404", description = "콘서트 없음")
    })
    public ResponseEntity<CommonResponse<SetlistCandidatesResponse>> candidates(
            @Parameter(description = "콘서트 PK") @PathVariable Long concertId) {
        return ResponseEntity.ok(CommonResponse.success(voteService.candidates(concertId)));
    }
}
