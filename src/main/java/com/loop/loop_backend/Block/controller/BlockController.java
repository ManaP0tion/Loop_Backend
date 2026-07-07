package com.loop.loop_backend.Block.controller;

import com.loop.loop_backend.Block.dto.BlockRequestDto;
import com.loop.loop_backend.Block.dto.BlockedUserResponseDto;
import com.loop.loop_backend.Block.service.BlockService;
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

import java.util.List;

@RestController
@RequestMapping("/api/users/me/blocks")
@RequiredArgsConstructor
@Tag(name = "Block", description = "사용자 차단 API")
public class BlockController {

    private final BlockService blockService;

    @GetMapping
    @Operation(summary = "차단 목록 조회", description = "내가 차단한 사용자 목록을 조회합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<CommonResponse<List<BlockedUserResponseDto>>> getBlockedUsers(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(CommonResponse.success(blockService.getBlockedUsers(userId)));
    }

    @PostMapping
    @Operation(summary = "사용자 차단", description = "특정 사용자를 차단합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "차단 성공"),
            @ApiResponse(responseCode = "400", description = "자기 자신을 차단하려는 경우",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"자기 자신을 신고/차단할 수 없습니다.\",\"code\":400}"))),
            @ApiResponse(responseCode = "404", description = "사용자 없음",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"사용자를 찾을 수 없습니다.\",\"code\":404}"))),
            @ApiResponse(responseCode = "409", description = "이미 차단한 사용자",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"이미 차단한 사용자입니다.\",\"code\":409}")))
    })
    public ResponseEntity<CommonResponse<Void>> block(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody BlockRequestDto requestDto) {
        blockService.block(userId, requestDto.getTargetUserId());
        return ResponseEntity.ok(CommonResponse.success("차단되었습니다.", null));
    }

    @DeleteMapping("/{targetUserId}")
    @Operation(summary = "사용자 차단 해제", description = "특정 사용자의 차단을 해제합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "차단 해제 성공"),
            @ApiResponse(responseCode = "404", description = "차단 내역 없음",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"차단 내역을 찾을 수 없습니다.\",\"code\":404}")))
    })
    public ResponseEntity<CommonResponse<Void>> unblock(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "차단 해제할 사용자 PK") @PathVariable Long targetUserId) {
        blockService.unblock(userId, targetUserId);
        return ResponseEntity.ok(CommonResponse.success("차단이 해제되었습니다.", null));
    }
}