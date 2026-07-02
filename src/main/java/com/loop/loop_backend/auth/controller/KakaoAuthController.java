package com.loop.loop_backend.auth.controller;

import com.loop.loop_backend.auth.dto.TokenResponseDto;
import com.loop.loop_backend.auth.service.KakaoAuthService;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.util.CookieUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 API")
public class KakaoAuthController {

    @Value("${frontend.url}")
    private String frontendUrl;

    private final KakaoAuthService kakaoAuthService;
    private final CookieUtil cookieUtil;

    @GetMapping("/oauth/kakao/callback")
    @Operation(summary = "카카오 로그인 콜백", description = "description = 로그인 성공 시 프론트엔드로 리다이렉트하며, 실패 시 로그인 페이지(FRONTEND_URL + \"/login?error=\" + encoded)로 에러 파라미터와 함께 리다이렉트합니다.")
    public void kakaoCallback(
            @RequestParam String code,
            HttpServletResponse response) throws IOException {

        try {
            // 1. 카카오 로그인 처리 + JWT 발급
            TokenResponseDto token = kakaoAuthService.login(code);

            // 2. Access/Refresh Token → HttpOnly Cookie
            response.addHeader(HttpHeaders.SET_COOKIE, cookieUtil.createAccessTokenCookie(token.getAccessToken()).toString());
            response.addHeader(HttpHeaders.SET_COOKIE, cookieUtil.createRefreshTokenCookie(token.getRefreshToken()).toString());

            // 3. 프론트로 리다이렉트 (프론트 주소 확인 필요)
            response.sendRedirect(frontendUrl);
        } catch (Exception e) {
            log.error("카카오 로그인 실패", e);
            String message = (e instanceof BusinessException be)
                    ? be.getErrorCode().getMessage()
                    : "카카오 로그인에 실패했습니다. 다시 시도해주세요.";
            String encoded = URLEncoder.encode(message, StandardCharsets.UTF_8);
            response.sendRedirect(frontendUrl + "/login?error=" + encoded);
        }
    }
}