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

    @Override
    public void sendCode(Long userId, String email) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));

        redisTemplate.opsForValue().set(codeKey(userId), code, CODE_TTL);
        redisTemplate.opsForValue().set(emailKey(userId), email, CODE_TTL);

        mailService.sendVerificationCode(email, code);
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

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.verifyEmail(email);
    }
}