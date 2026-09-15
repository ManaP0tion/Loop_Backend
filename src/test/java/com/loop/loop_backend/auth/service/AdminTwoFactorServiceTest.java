package com.loop.loop_backend.auth.service;

import com.loop.loop_backend.Mail.service.MailService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminTwoFactorServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock MailService mailService;
    @InjectMocks AdminTwoFactorService adminTwoFactorService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void 새_코드_발급시_같은_관리자의_이전_미사용_코드가_즉시_무효화된다() {
        // 관리자 7 의 직전 challenge 가 아직 살아있는 상태
        when(valueOperations.get("admin_2fa_current:7")).thenReturn("old-challenge");

        adminTwoFactorService.startChallenge(7L, "admin@example.com");

        // 이전 challenge 의 code/user/fail 키가 전부 삭제돼야 함 (옛 코드 무효화)
        verify(redisTemplate).delete("admin_2fa_code:old-challenge");
        verify(redisTemplate).delete("admin_2fa_user:old-challenge");
        verify(redisTemplate).delete("admin_2fa_fail:old-challenge");
        // 새 코드가 실제로 발송됨
        verify(mailService).sendVerificationCode(eq("admin@example.com"), anyString());
    }

    @Test
    void 이전_코드가_없으면_삭제_없이_새_코드만_발급된다() {
        when(valueOperations.get("admin_2fa_current:7")).thenReturn(null);

        adminTwoFactorService.startChallenge(7L, "admin@example.com");

        // 지울 이전 challenge 가 없으니 delete 호출 안 함
        verify(redisTemplate, never()).delete(anyString());
        verify(mailService).sendVerificationCode(eq("admin@example.com"), anyString());
    }
}
