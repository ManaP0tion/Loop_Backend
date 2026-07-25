package com.loop.loop_backend.auth.controller;

import com.loop.loop_backend.auth.dto.AccessTokenResponseDto;
import com.loop.loop_backend.auth.dto.KakaoLoginRequestDto;
import com.loop.loop_backend.auth.dto.TokenResponseDto;
import com.loop.loop_backend.auth.service.KakaoAuthService;
import com.loop.loop_backend.common.exception.CommonResponse;
import com.loop.loop_backend.common.util.CookieUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 API")
public class KakaoAuthController {

    private final KakaoAuthService kakaoAuthService;
    private final CookieUtil cookieUtil;

    @PostMapping("/kakao/login")
    @Operation(summary = "카카오 로그인", description = "프론트에서 카카오 인가 코드를 받아 로그인을 처리합니다. " +
            "Refresh Token은 HttpOnly 쿠키로 내려주고, Access Token은 응답 바디로 반환합니다.")
    public ResponseEntity<CommonResponse<AccessTokenResponseDto>> kakaoLogin(
            @Valid @RequestBody KakaoLoginRequestDto requestDto) {

        TokenResponseDto token = kakaoAuthService.login(requestDto.getCode(), requestDto.getRedirectUri());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtil.createRefreshTokenCookie(token.getRefreshToken()).toString())
                .body(CommonResponse.success(new AccessTokenResponseDto(token.getAccessToken())));
    }
}