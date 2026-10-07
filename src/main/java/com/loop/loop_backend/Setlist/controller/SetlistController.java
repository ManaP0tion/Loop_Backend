package com.loop.loop_backend.Setlist.controller;

import com.loop.loop_backend.Setlist.dto.SetlistCandidatesResponse;
import com.loop.loop_backend.Setlist.service.SetlistVoteService;
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

/** 예상 셋리스트 조회(3-3·3-4). 비로그인 열람 - GET /api/concerts/** 는 SecurityConfig에서 permitAll. */
@RestController
@RequestMapping("/api/concerts/{concertId}/setlist")
@RequiredArgsConstructor
@Tag(name = "Setlist", description = "예상 셋리스트 조회 (비로그인 가능). 투표는 /api/users/me/setlist-votes")
public class SetlistController {

    private final SetlistVoteService voteService;

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
