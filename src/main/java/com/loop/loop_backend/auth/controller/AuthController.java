package com.loop.loop_backend.auth.controller;

import com.loop.loop_backend.auth.dto.LoginRequestDto;
import com.loop.loop_backend.auth.dto.TokenResponseDto;
import com.loop.loop_backend.auth.service.AuthService;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.CommonResponse;
import com.loop.loop_backend.common.exception.ErrorCode;
import com.loop.loop_backend.common.util.CookieUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 API")
public class AuthController {

    private final AuthService authService;
    private final CookieUtil cookieUtil;

//    @PostMapping("/login")
//    @Operation(summary = "로그인", description = "아이디/비밀번호로 로그인하고 Access/Refresh Token을 HttpOnly 쿠키로 발급받습니다")
//    @ApiResponses({
//            @ApiResponse(responseCode = "200", description = "로그인 성공"),
//            @ApiResponse(responseCode = "401", description = "아이디 또는 비밀번호가 올바르지 않음"),
//            @ApiResponse(responseCode = "429", description = "로그인 실패 횟수 초과로 잠금")
//    })
//    public ResponseEntity<CommonResponse<Void>> login(
//            @Valid @RequestBody LoginRequestDto requestDto) {
//        TokenResponseDto token = authService.login(requestDto);
//        return ResponseEntity.ok()
//                .header(HttpHeaders.SET_COOKIE, cookieUtil.createAccessTokenCookie(token.getAccessToken()).toString())
//                .header(HttpHeaders.SET_COOKIE, cookieUtil.createRefreshTokenCookie(token.getRefreshToken()).toString())
//                .body(CommonResponse.success(null));
//    }

    @PostMapping("/refresh")
    @Operation(summary = "토큰 재발급", description = "쿠키의 Refresh Token으로 Access/Refresh Token을 재발급하여 쿠키로 내려줍니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재발급 성공"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 Refresh Token",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"유효하지 않은 Refresh Token입니다.\",\"code\":401}")))
    })
    public ResponseEntity<CommonResponse<Void>> refresh(
            @Parameter(hidden = true)
            @CookieValue(value = "refreshToken", required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        TokenResponseDto token = authService.reissue(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtil.createAccessTokenCookie(token.getAccessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, cookieUtil.createRefreshTokenCookie(token.getRefreshToken()).toString())
                .body(CommonResponse.success(null));
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "Refresh Token을 삭제하고 인증 쿠키를 만료시킵니다")
    @ApiResponse(responseCode = "200", description = "로그아웃 성공")
    public ResponseEntity<CommonResponse<Void>> logout(@AuthenticationPrincipal Long userId) {
        authService.logout(userId);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtil.expireAccessTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieUtil.expireRefreshTokenCookie().toString())
                .body(CommonResponse.success("로그아웃 되었습니다.", null));
    }
}