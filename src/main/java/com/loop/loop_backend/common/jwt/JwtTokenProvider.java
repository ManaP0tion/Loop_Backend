package com.loop.loop_backend.common.jwt;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.*;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtTokenProvider {

    // Access/Refresh 는 만료시간만 다를 뿐 구조가 같아서, 타입 claim 이 없으면
    // 14일짜리 Refresh Token 을 그대로 Bearer 로 써서 30분 만료를 우회할 수 있다.
    private static final String TYPE_CLAIM = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    // Access Token 생성
    public String createAccessToken(Long userId) {
        return createToken(userId, TYPE_ACCESS, accessTokenExpiration);
    }

    // Refresh Token 생성
    public String createRefreshToken(Long userId) {
        return createToken(userId, TYPE_REFRESH, refreshTokenExpiration);
    }

    private String createToken(Long userId, String type, long expiration) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TYPE_CLAIM, type)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    // 토큰 만료까지 남은 시간(ms). 이미 만료면 0 이하.
    public long getRemainingMillis(String token) {
        Date expiration = parseClaims(token).getExpiration();
        return expiration.getTime() - System.currentTimeMillis();
    }

    // 토큰에서 userId 추출
    public Long getUserId(String token) {
        Claims claims = parseClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    // API 인증용 토큰인지 검증 (Refresh Token 은 여기서 걸러진다)
    public boolean validateAccessToken(String token) {
        return validate(token, TYPE_ACCESS);
    }

    // 재발급용 토큰인지 검증 (Access Token 은 여기서 걸러진다)
    public boolean validateRefreshToken(String token) {
        return validate(token, TYPE_REFRESH);
    }

    private boolean validate(String token, String expectedType) {
        try {
            return expectedType.equals(parseClaims(token).get(TYPE_CLAIM, String.class));
        } catch (ExpiredJwtException e) {
            // 만료된 토큰 — 프론트가 401 받고 refresh 호출하게 됨
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            // 위조/형식 오류/타입 claim 누락(구버전 토큰) 등
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

}
