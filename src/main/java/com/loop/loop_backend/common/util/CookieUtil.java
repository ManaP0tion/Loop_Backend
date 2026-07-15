package com.loop.loop_backend.common.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    public ResponseCookie createRefreshTokenCookie(String refreshToken) {
        return buildCookie("refreshToken", refreshToken, refreshTokenExpiration / 1000);
    }

    public ResponseCookie expireRefreshTokenCookie() {
        return buildCookie("refreshToken", "", 0);
    }

    private ResponseCookie buildCookie(String name, String value, long maxAgeSeconds) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite("None")
                .build();
    }
}