package com.loop.loop_backend.CompanionHeart.controller;

import com.loop.loop_backend.CompanionHeart.dto.HeartedConcertDetailDto;
import com.loop.loop_backend.CompanionHeart.dto.HeartedConcertSummaryDto;
import com.loop.loop_backend.CompanionHeart.service.CompanionHeartService;
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

@RestController
@RequestMapping("/api/companions")
@RequiredArgsConstructor
@Tag(name = "CompanionHeart", description = "동행 프로필 하트 API")
public class CompanionHeartController {

    private final CompanionHeartService companionHeartService;

    @PostMapping("/{id}/heart")
    @Operation(summary = "동행 프로필 하트", description = "동행 프로필에 하트를 누릅니다. 이미 하트한 상태면 그대로 성공 처리됩니다. 본인 글에는 누를 수 없습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 성공"),
            @ApiResponse(responseCode = "400", description = "본인 글에 하트 시도",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"본인 게시글에는 하트를 누를 수 없습니다.\",\"code\":400}"))),
            @ApiResponse(responseCode = "404", description = "동행 프로필 없음",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"동행 모집글을 찾을 수 없습니다.\",\"code\":404}")))
    })
    public ResponseEntity<CommonResponse<Void>> heartCompanion(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "동행 프로필 PK") @PathVariable Long id) {
        companionHeartService.heartCompanion(userId, id);
        return ResponseEntity.ok(CommonResponse.success("하트를 눌렀습니다.", null));
    }

    @DeleteMapping("/{id}/heart")
    @Operation(summary = "동행 프로필 하트 취소", description = "동행 프로필 하트를 취소합니다. 하트하지 않은 상태여도 그대로 성공 처리됩니다.")
    @ApiResponse(responseCode = "200", description = "처리 성공")
    public ResponseEntity<CommonResponse<Void>> unheartCompanion(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "동행 프로필 PK") @PathVariable Long id) {
        companionHeartService.unheartCompanion(userId, id);
        return ResponseEntity.ok(CommonResponse.success("하트를 취소했습니다.", null));
    }

    @GetMapping("/me/hearts")
    @Operation(summary = "하트 탭 메인 조회", description = "내가 하트한 프로필을 콘서트별로 묶어서 조회합니다. " +
            "콘서트별로 최근 하트한 순 미리보기 최대 2개와 전체 개수가 함께 내려갑니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<CommonResponse<List<HeartedConcertSummaryDto>>> getMyHeartedConcerts(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(CommonResponse.success(companionHeartService.getMyHeartedConcerts(userId)));
    }

    @GetMapping("/me/hearts/{concertId}")
    @Operation(summary = "하트 탭 콘서트 상세 조회", description = "특정 콘서트에서 내가 하트한 프로필 전체를 관람일(day)별로 묶어서 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "콘서트 없음",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"콘서트를 찾을 수 없습니다.\",\"code\":404}")))
    })
    public ResponseEntity<CommonResponse<HeartedConcertDetailDto>> getMyHeartedCompanionsByConcert(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "콘서트 PK") @PathVariable Long concertId) {
        return ResponseEntity.ok(CommonResponse.success(
                companionHeartService.getMyHeartedCompanionsByConcert(userId, concertId)));
    }
}