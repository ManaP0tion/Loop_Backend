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
    private static final int HOURLY_SEND_LIMIT = 5;
    private static final Duration HOURLY_SEND_WINDOW = Duration.ofHours(1);
    private static final int DAILY_SEND_LIMIT = 10;
    private static final Duration DAILY_SEND_WINDOW = Duration.ofDays(1);
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

    @Override
    public void sendCode(Long userId, String email) {
        enforceSendLimit(hourlySendCountKey(userId), HOURLY_SEND_WINDOW, HOURLY_SEND_LIMIT);
        enforceSendLimit(dailySendCountKey(userId), DAILY_SEND_WINDOW, DAILY_SEND_LIMIT);

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));

        redisTemplate.opsForValue().set(codeKey(userId), code, CODE_TTL);
        redisTemplate.opsForValue().set(emailKey(userId), email, CODE_TTL);

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
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH);
        }

        redisTemplate.delete(codeKey(userId));
        redisTemplate.delete(emailKey(userId));

        userRepository.findByEmail(email)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
                });

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.verifyEmail(email);
    }
}