package com.loop.loop_backend.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * 로그아웃된 Access Token 블랙리스트. TTL = 토큰 잔여 만료시간이라 자동 소멸(청소 불필요).
 */
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;

    private String key(String token) {
        return "blacklist:" + sha256(token);
    }

    public void blacklist(String token, long ttlMillis) {
        if (ttlMillis <= 0) return; // 이미 만료된 토큰은 저장 불필요
        redisTemplate.opsForValue().set(key(token), "1", Duration.ofMillis(ttlMillis));
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(token)));
    }

    // 원문 토큰(자격증명)을 그대로 Redis 키에 두지 않도록 해시
    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256은 항상 존재
        }
    }
}
