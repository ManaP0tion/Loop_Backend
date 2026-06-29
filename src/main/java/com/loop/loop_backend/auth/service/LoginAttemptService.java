package com.loop.loop_backend.auth.service;


import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final StringRedisTemplate redisTemplate;

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private String key(String userId) {
        return "login_fail:" + userId;
    }

    public void increaseFailCount(String userId) {
        String key = key(userId);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            // 첫 실패일 때만 TTL 설정 (이후 increment는 TTL 유지)
            redisTemplate.expire(key, LOCK_DURATION);
        }
    }

    public void resetFailCount(String userId) {
        redisTemplate.delete(key(userId));
    }

    public boolean isLocked(String userId) {
        String value = redisTemplate.opsForValue().get(key(userId));
        return value != null && Integer.parseInt(value) >= MAX_ATTEMPTS;
    }

    public Long getRemainingLockSeconds(String userId) {
        return redisTemplate.getExpire(key(userId));
    }
}
