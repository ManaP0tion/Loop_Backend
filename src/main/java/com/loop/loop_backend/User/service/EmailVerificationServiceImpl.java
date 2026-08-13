package com.loop.loop_backend.User.service;

import com.loop.loop_backend.Mail.service.MailService;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private static final Duration CODE_TTL = Duration.ofMinutes(3);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(10);
    private static final int HOURLY_SEND_LIMIT = 5;
    private static final Duration HOURLY_SEND_WINDOW = Duration.ofHours(1);
    private static final int DAILY_SEND_LIMIT = 10;
    private static final Duration DAILY_SEND_WINDOW = Duration.ofDays(1);
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final MailService mailService;
    private final UserRepository userRepository;

    private String codeKey(Long userId) {
        return "email_verify_code:" + userId;
    }

    private String emailKey(Long userId) {
        return "email_verify_email:" + userId;
    }

    private String hourlySendCountKey(Long userId) {
        return "email_verify_send_count_hour:" + userId;
    }

    private String dailySendCountKey(Long userId) {
        return "email_verify_send_count_day:" + userId;
    }

    private String cooldownKey(Long userId) {
        return "email_verify_cooldown:" + userId;
    }

    private String failCountKey(Long userId) {
        return "email_verify_fail:" + userId;
    }

    @Override
    public void sendCode(Long userId, String email) {
        // 10초 이내 재요청은 응답까지 평소와 완전히 동일하게, 그냥 조용히 무시한다.
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(cooldownKey(userId), "1", RESEND_COOLDOWN);
        if (Boolean.FALSE.equals(acquired)) {
            return;
        }

        enforceSendLimit(hourlySendCountKey(userId), HOURLY_SEND_WINDOW, HOURLY_SEND_LIMIT);
        enforceSendLimit(dailySendCountKey(userId), DAILY_SEND_WINDOW, DAILY_SEND_LIMIT);

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));

        redisTemplate.opsForValue().set(codeKey(userId), code, CODE_TTL);
        redisTemplate.opsForValue().set(emailKey(userId), email, CODE_TTL);
        // 새 코드 발급 → 이전 실패치가 새 코드를 즉시 무효화하지 않도록 리셋
        redisTemplate.delete(failCountKey(userId));

        mailService.sendVerificationCode(email, code);
    }

    // count == 1(창의 첫 요청)일 때만 TTL을 걸어 새 창을 연다 (이후 increment는 기존 TTL 유지) - LoginAttemptService와 동일한 패턴
    private void enforceSendLimit(String countKey, Duration window, int limit) {
        Long count = redisTemplate.opsForValue().increment(countKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(countKey, window);
        }
        if (count != null && count > limit) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_SEND_LIMIT_EXCEEDED);
        }
    }

    @Override
    @Transactional
    public void verifyCode(Long userId, String email, String code) {
        String storedEmail = redisTemplate.opsForValue().get(emailKey(userId));
        String storedCode = redisTemplate.opsForValue().get(codeKey(userId));

        if (storedEmail == null || storedCode == null
                || !storedEmail.equals(email) || !storedCode.equals(code)) {
            registerFailure(userId);
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH);
        }

        redisTemplate.delete(codeKey(userId));
        redisTemplate.delete(emailKey(userId));
        redisTemplate.delete(failCountKey(userId));

        userRepository.findByEmail(email)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
                });

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.verifyEmail(email);
    }

    // 실패 카운터 증가 (첫 실패에만 코드 TTL과 동일한 3분 창을 연다). 5회 도달 시 코드를 삭제해
    // 이후 시도는 storedCode == null 로 자연히 막힌다 — brute-force 차단.
    private void registerFailure(Long userId) {
        Long count = redisTemplate.opsForValue().increment(failCountKey(userId));
        if (count != null && count == 1L) {
            redisTemplate.expire(failCountKey(userId), CODE_TTL);
        }
        if (count != null && count >= MAX_VERIFY_ATTEMPTS) {
            redisTemplate.delete(codeKey(userId));
            redisTemplate.delete(emailKey(userId));
        }
    }
}