package com.loop.loop_backend.auth.controller;

import com.loop.loop_backend.auth.dto.LoginRequestDto;
import com.loop.loop_backend.auth.dto.RefreshRequestDto;
import com.loop.loop_backend.auth.dto.TokenResponseDto;
import com.loop.loop_backend.auth.service.AuthService;
import com.loop.loop_backend.common.exception.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 API")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "아이디/비밀번호로 로그인하고 Access/Refresh Token을 발급받습니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "401", description = "아이디 또는 비밀번호가 올바르지 않음"),
            @ApiResponse(responseCode = "429", description = "로그인 실패 횟수 초과로 잠금")
    })
    public ResponseEntity<CommonResponse<TokenResponseDto>> login(
            @Valid @RequestBody LoginRequestDto requestDto) {
        return ResponseEntity.ok(CommonResponse.success(authService.login(requestDto)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "토큰 재발급", description = "Refresh Token으로 Access/Refresh Token을 재발급합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재발급 성공"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 Refresh Token")
    })
    public ResponseEntity<CommonResponse<TokenResponseDto>> refresh(
            @Valid @RequestBody RefreshRequestDto requestDto) {
        return ResponseEntity.ok(CommonResponse.success(authService.reissue(requestDto)));
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "Refresh Token을 삭제하고 로그아웃합니다")
    @ApiResponse(responseCode = "204", description = "로그아웃 성공")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Long userId) {
        authService.logout(userId);
        return ResponseEntity.noContent().build();
    }
}