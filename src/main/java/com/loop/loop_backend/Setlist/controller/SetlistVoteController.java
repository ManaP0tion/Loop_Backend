package com.loop.loop_backend.Setlist.controller;

import com.loop.loop_backend.Setlist.dto.MySetlistVoteResponse;
import com.loop.loop_backend.Setlist.dto.SetlistVoteRequest;
import com.loop.loop_backend.Setlist.service.SetlistVoteService;
import com.loop.loop_backend.common.exception.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// /api/concerts/** 아래에 두지 않은 이유: 그 경로는 PUT이 ADMIN 전용이다. /api/users/me/** 는 인증 필수(스크랩과 같은 이유).
@RestController
@RequestMapping("/api/users/me/setlist-votes/{concertId}")
@RequiredArgsConstructor
@Tag(name = "SetlistVote", description = "내 예상 셋리스트 투표 (로그인 필수)")
public class SetlistVoteController {

    private static final String INVALID_INPUT_EXAMPLE =
            "{\"success\":false,\"message\":\"입력값이 올바르지 않습니다.\",\"code\":400}";
    private static final String CLOSED_EXAMPLE =
            "{\"success\":false,\"message\":\"예상 셋리스트 투표가 마감되었습니다.\",\"code\":409}";

    private final SetlistVoteService voteService;

    @GetMapping
    @Operation(summary = "내 투표 조회", description = "수정할 때 기존 선택을 체크된 상태로 보여주는 데 쓴다. 투표하지 않았으면 voted=false.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "콘서트 없음")
    })
    public ResponseEntity<CommonResponse<MySetlistVoteResponse>> myVote(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "콘서트 PK") @PathVariable Long concertId) {
        return ResponseEntity.ok(CommonResponse.success(voteService.myVote(concertId, userId)));
    }

    @PutMapping
    @Operation(summary = "투표 저장·수정",
            description = "공연당 1건. 이미 투표했으면 기존 선택을 통째로 교체한다(집계에 즉시 반영).\n\n" +
                    "- 1곡 이상, 최대 n곡(예상 곡 수). n곡 미만 제출 가능\n" +
                    "- 마감 = 공연 시작일 00:00 KST(서버 시각). 마감 전까지 횟수 제한 없이 수정")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공"),
            @ApiResponse(responseCode = "400", description = "곡 0개·n곡 초과, 공연 아티스트의 곡이 아님(삭제된 곡 포함), 셋리스트를 운영하지 않는 공연",
                    content = @Content(examples = @ExampleObject(value = INVALID_INPUT_EXAMPLE))),
            @ApiResponse(responseCode = "403", description = "오픈 예정(비공개) 공연 - CONCERT_NOT_OPEN"),
            @ApiResponse(responseCode = "404", description = "콘서트 없음"),
            @ApiResponse(responseCode = "409", description = "투표 마감",
                    content = @Content(examples = @ExampleObject(value = CLOSED_EXAMPLE)))
    })
    public ResponseEntity<CommonResponse<MySetlistVoteResponse>> saveVote(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "콘서트 PK") @PathVariable Long concertId,
            @Valid @RequestBody SetlistVoteRequest body) {
        return ResponseEntity.ok(CommonResponse.success(voteService.saveVote(concertId, userId, body.songIds())));
    }
}
