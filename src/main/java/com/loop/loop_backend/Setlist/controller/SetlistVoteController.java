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
@RequestMapping("/api/users/me/setlist-votes/{concertId}/performances/{performanceId}")
@RequiredArgsConstructor
@Tag(name = "SetlistVote", description = "내 예상 셋리스트 투표 (로그인 필수). 출연 정보(단독 공연·페스티벌 헤드라이너)마다 따로 투표한다")
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
            @ApiResponse(responseCode = "404", description = "출연 정보 없음 - PERFORMANCE_NOT_FOUND")
    })
    public ResponseEntity<CommonResponse<MySetlistVoteResponse>> myVote(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "콘서트 PK") @PathVariable Long concertId,
            @Parameter(description = "출연 정보 PK - 단독 공연은 공연 상세의 performanceId, 페스티벌은 라인업의 lineupId(헤드라이너)") @PathVariable Long performanceId) {
        return ResponseEntity.ok(CommonResponse.success(voteService.myVote(concertId, performanceId, userId)));
    }

    @PutMapping("/result-mail-consent")
    @Operation(summary = "결과 메일 수신 동의",
            description = "투표 완료 후 모달(MD03) 1단계. 이미 동의한 상태(resultMailConsent=true)면 모달을 띄우지 않는다.\n\n" +
                    "실제 발송 조건: 동의 + 이메일 인증 완료 + 알림 설정의 셋리스트 결과 알림 ON. " +
                    "인증 안 된 계정은 이어서 이메일 입력·인증(2단계)을 진행한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "동의 저장(이미 동의했어도 200)"),
            @ApiResponse(responseCode = "404", description = "출연 정보 없음, 또는 투표하지 않음 - SETLIST_VOTE_NOT_FOUND")
    })
    public ResponseEntity<CommonResponse<MySetlistVoteResponse>> agreeResultMail(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "콘서트 PK") @PathVariable Long concertId,
            @Parameter(description = "출연 정보 PK - 단독 공연은 공연 상세의 performanceId, 페스티벌은 라인업의 lineupId(헤드라이너)") @PathVariable Long performanceId) {
        return ResponseEntity.ok(CommonResponse.success(voteService.agreeResultMail(concertId, performanceId, userId)));
    }

    @PutMapping
    @Operation(summary = "투표 저장·수정",
            description = "출연 정보당 1건. 이미 투표했으면 기존 선택을 통째로 교체한다(집계에 즉시 반영).\n\n" +
                    "- 1곡 이상, 최대 n곡(예상 곡 수). n곡 미만 제출 가능\n" +
                    "- 마감 = 공연 시작일 00:00 KST(서버 시각, 페스티벌도 동일). 마감 전까지 횟수 제한 없이 수정")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공"),
            @ApiResponse(responseCode = "400", description = "곡 0개·n곡 초과, 출연 아티스트의 곡이 아님(삭제된 곡 포함), 셋리스트를 운영하지 않는 출연 정보(페스티벌 비헤드라이너)",
                    content = @Content(examples = @ExampleObject(value = INVALID_INPUT_EXAMPLE))),
            @ApiResponse(responseCode = "403", description = "오픈 예정(비공개) 공연 - CONCERT_NOT_OPEN"),
            @ApiResponse(responseCode = "404", description = "출연 정보 없음 - PERFORMANCE_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "투표 마감",
                    content = @Content(examples = @ExampleObject(value = CLOSED_EXAMPLE)))
    })
    public ResponseEntity<CommonResponse<MySetlistVoteResponse>> saveVote(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "콘서트 PK") @PathVariable Long concertId,
            @Parameter(description = "출연 정보 PK - 단독 공연은 공연 상세의 performanceId, 페스티벌은 라인업의 lineupId(헤드라이너)") @PathVariable Long performanceId,
            @Valid @RequestBody SetlistVoteRequest body) {
        return ResponseEntity.ok(CommonResponse.success(voteService.saveVote(concertId, performanceId, userId, body.songIds())));
    }
}
