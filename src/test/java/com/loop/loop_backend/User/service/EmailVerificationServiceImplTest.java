package com.loop.loop_backend.User.service;

import com.loop.loop_backend.Mail.service.MailService;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailVerificationServiceImplTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock MailService mailService;
    @Mock UserRepository userRepository;
    @InjectMocks EmailVerificationServiceImpl emailVerificationService;

    private static final Long USER_ID = 1L;
    private static final String EMAIL = "user@example.com";
    private static final String CODE_KEY = "email_verify_code:" + USER_ID;
    private static final String EMAIL_KEY = "email_verify_email:" + USER_ID;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private User testUser() {
        return User.builder()
                .authProvider(AuthProvider.KAKAO)
                .providerId("kakao-1")
                .status(Status.ACTIVE)
                .onboardingCompleted(true)
                .build();
    }

    // ── sendCode ──────────────────────────────────────────────────────────

    @Test
    void 코드_발송시_6자리_코드를_생성해_Redis에_저장하고_같은_코드로_메일을_보낸다() {
        emailVerificationService.sendCode(USER_ID, EMAIL);

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(CODE_KEY), codeCaptor.capture(), eq(Duration.ofMinutes(3)));
        verify(valueOperations).set(eq(EMAIL_KEY), eq(EMAIL), eq(Duration.ofMinutes(3)));

        String generatedCode = codeCaptor.getValue();
        assertThat(generatedCode).matches("\\d{6}");
        verify(mailService).sendVerificationCode(EMAIL, generatedCode);
    }

    // ── verifyCode ────────────────────────────────────────────────────────

    @Test
    void 코드와_이메일이_일치하면_인증에_성공하고_User_email이_저장된다() {
        User user = testUser();
        when(valueOperations.get(EMAIL_KEY)).thenReturn(EMAIL);
        when(valueOperations.get(CODE_KEY)).thenReturn("123456");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        emailVerificationService.verifyCode(USER_ID, EMAIL, "123456");

        assertThat(user.getEmail()).isEqualTo(EMAIL);
        verify(redisTemplate).delete(CODE_KEY);
        verify(redisTemplate).delete(EMAIL_KEY);
    }

    @Test
    void 발송된_코드가_없거나_만료된_경우_예외를_던지고_User는_건드리지_않는다() {
        when(valueOperations.get(any())).thenReturn(null);

        assertThatThrownBy(() -> emailVerificationService.verifyCode(USER_ID, EMAIL, "123456"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH);

        verifyNoInteractions(userRepository);
    }

    @Test
    void 코드가_틀리면_예외를_던지고_User는_건드리지_않는다() {
        when(valueOperations.get(EMAIL_KEY)).thenReturn(EMAIL);
        when(valueOperations.get(CODE_KEY)).thenReturn("999999");

        assertThatThrownBy(() -> emailVerificationService.verifyCode(USER_ID, EMAIL, "123456"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH);

        verifyNoInteractions(userRepository);
    }

    @Test
    void 인증_요청한_이메일이_발송받은_이메일과_다르면_예외를_던진다() {
        when(valueOperations.get(EMAIL_KEY)).thenReturn(EMAIL);
        when(valueOperations.get(CODE_KEY)).thenReturn("123456");

        assertThatThrownBy(() -> emailVerificationService.verifyCode(USER_ID, "other@example.com", "123456"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH);

        verifyNoInteractions(userRepository);
    }
}