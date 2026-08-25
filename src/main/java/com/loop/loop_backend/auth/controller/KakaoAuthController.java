package com.loop.loop_backend.auth.controller;

import com.loop.loop_backend.auth.dto.AccessTokenResponseDto;
import com.loop.loop_backend.auth.dto.AdminTwoFactorVerifyRequestDto;
import com.loop.loop_backend.auth.dto.KakaoLoginRequestDto;
import com.loop.loop_backend.auth.dto.KakaoLoginResult;
import com.loop.loop_backend.auth.dto.TokenResponseDto;
import com.loop.loop_backend.auth.dto.TwoFactorChallengeResponseDto;
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

import java.util.Map;

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
    public ResponseEntity<CommonResponse<?>> kakaoLogin(
            @Valid @RequestBody KakaoLoginRequestDto requestDto) {

        KakaoLoginResult result = kakaoAuthService.login(requestDto.getCode(), requestDto.getRedirectUri());

        // 관리자: 토큰 대신 challengeId만 내려준다. 코드 검증(/kakao/2fa/verify) 후 로그인이 완료된다.
        if (result.twoFactorRequired()) {
            return ResponseEntity.ok(CommonResponse.success(
                    new TwoFactorChallengeResponseDto(result.challengeId())));
        }

        TokenResponseDto token = result.tokens();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtil.createRefreshTokenCookie(token.getRefreshToken()).toString())
                .body(CommonResponse.success(new AccessTokenResponseDto(token.getAccessToken())));
    }

    @PostMapping("/kakao/2fa/verify")
    @Operation(summary = "관리자 2단계 인증 확인",
            description = "카카오 로그인 시 관리자에게 반환된 challengeId와 이메일로 발송된 코드를 검증한다. " +
                    "성공 시 일반 로그인과 동일하게 Access Token은 바디로, Refresh Token은 HttpOnly 쿠키로 내려준다.")
    public ResponseEntity<CommonResponse<AccessTokenResponseDto>> verifyAdminTwoFactor(
            @Valid @RequestBody AdminTwoFactorVerifyRequestDto requestDto) {

        TokenResponseDto token = kakaoAuthService.completeAdminLogin(requestDto.getChallengeId(), requestDto.getCode());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtil.createRefreshTokenCookie(token.getRefreshToken()).toString())
                .body(CommonResponse.success(new AccessTokenResponseDto(token.getAccessToken())));
    }

    @GetMapping("/kakao/client-id")
    @Operation(summary = "카카오 REST API 키 조회", description = "어드민 페이지가 OAuth 인가 URL을 만들기 위해 사용.")
    public ResponseEntity<CommonResponse<Map<String, String>>> kakaoClientId() {
        return ResponseEntity.ok(CommonResponse.success(Map.of("clientId", kakaoAuthService.getClientId())));
    }
}