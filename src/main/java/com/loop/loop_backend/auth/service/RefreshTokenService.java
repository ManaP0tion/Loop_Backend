package com.loop.loop_backend.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private String key(Long userId) {
        return "refresh_token:" + userId;
    }

    public void save(Long userId, String refreshToken) {
        redisTemplate.opsForValue().set(key(userId), refreshToken, Duration.ofMillis(refreshTokenExpiration));
    }

    public boolean isValid(Long userId, String refreshToken) {
        String saved = redisTemplate.opsForValue().get(key(userId));
        return refreshToken.equals(saved);
    }

    public void delete(Long userId) {
        redisTemplate.delete(key(userId));
    }
}
