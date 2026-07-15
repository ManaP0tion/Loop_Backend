package com.loop.loop_backend.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginAttemptServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @InjectMocks LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void 첫_실패시_카운트_증가하고_TTL_설정된다() {
        when(valueOps.increment("login_fail:user1")).thenReturn(1L);

        loginAttemptService.increaseFailCount("user1");

        verify(valueOps).increment("login_fail:user1");
        verify(redisTemplate).expire(eq("login_fail:user1"), any(Duration.class));
    }

    @Test
    void 두번째_실패시_TTL을_다시_설정하지_않는다() {
        when(valueOps.increment("login_fail:user1")).thenReturn(2L);

        loginAttemptService.increaseFailCount("user1");

        verify(redisTemplate, never()).expire(any(), any(Duration.class));
    }

    @Test
    void 실패_횟수가_5회_미만이면_잠기지_않는다() {
        when(valueOps.get("login_fail:user1")).thenReturn("4");

        assertThat(loginAttemptService.isLocked("user1")).isFalse();
    }

    @Test
    void 실패_횟수가_5회_이상이면_잠긴다() {
        when(valueOps.get("login_fail:user1")).thenReturn("5");

        assertThat(loginAttemptService.isLocked("user1")).isTrue();
    }

    @Test
    void 실패_기록이_없으면_잠기지_않는다() {
        when(valueOps.get("login_fail:user1")).thenReturn(null);

        assertThat(loginAttemptService.isLocked("user1")).isFalse();
    }

    @Test
    void 실패_횟수_초기화시_키가_삭제된다() {
        loginAttemptService.resetFailCount("user1");

        verify(redisTemplate).delete("login_fail:user1");
    }
}
