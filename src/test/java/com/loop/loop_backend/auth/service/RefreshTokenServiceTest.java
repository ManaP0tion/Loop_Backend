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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshTokenServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @InjectMocks RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenExpiration", 1209600000L);
    }

    @Test
    void 토큰을_Redis에_저장한다() {
        refreshTokenService.save(1L, "refresh-token");

        verify(valueOps).set(
                eq("refresh_token:1"),
                eq("refresh-token"),
                eq(Duration.ofMillis(1209600000L))
        );
    }

    @Test
    void 저장된_토큰과_일치하면_유효하다() {
        when(valueOps.get("refresh_token:1")).thenReturn("refresh-token");

        assertThat(refreshTokenService.isValid(1L, "refresh-token")).isTrue();
    }

    @Test
    void 저장된_토큰과_다르면_유효하지_않다() {
        when(valueOps.get("refresh_token:1")).thenReturn("other-token");

        assertThat(refreshTokenService.isValid(1L, "refresh-token")).isFalse();
    }

    @Test
    void 저장된_토큰이_없으면_유효하지_않다() {
        when(valueOps.get("refresh_token:1")).thenReturn(null);

        assertThat(refreshTokenService.isValid(1L, "refresh-token")).isFalse();
    }

    @Test
    void 토큰_삭제시_키가_제거된다() {
        refreshTokenService.delete(1L);

        verify(redisTemplate).delete("refresh_token:1");
    }
}