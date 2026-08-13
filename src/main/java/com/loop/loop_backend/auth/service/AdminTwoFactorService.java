package com.loop.loop_backend.auth.service;

import com.loop.loop_backend.Mail.service.MailService;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

/**
 * 관리자 Kakao 로그인 2단계 인증(이메일 코드)의 코드 라이프사이클만 담당.
 * EmailVerificationServiceImpl과 동일한 Redis increment+TTL 패턴을 쓰지만,
 * 그쪽 verifyCode는 이메일 소유 검증이라 user.email을 세팅하는 부작용이 있어 재사용하지 않고 분리했다.
 * challengeId만 클라이언트에 노출하고 userId는 서버 Redis에만 보관 → 클라이언트가 대상 계정을 지정하지 못한다.
 */
@Service
@RequiredArgsConstructor
public class AdminTwoFactorService {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final MailService mailService;

    private String codeKey(String challengeId) { return "admin_2fa_code:" + challengeId; }
    private String userKey(String challengeId) { return "admin_2fa_user:" + challengeId; }
    private String failKey(String challengeId) { return "admin_2fa_fail:" + challengeId; }

    /** 6자리 코드를 생성·발송하고 challengeId를 반환한다. */
    public String startChallenge(Long userId, String email) {
        String challengeId = UUID.randomUUID().toString();
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));

        redisTemplate.opsForValue().set(codeKey(challengeId), code, CODE_TTL);
        redisTemplate.opsForValue().set(userKey(challengeId), String.valueOf(userId), CODE_TTL);

        mailService.sendVerificationCode(email, code);
        return challengeId;
    }

    /** 코드 검증 후 대상 userId를 반환. 5회 실패 시 challenge를 무효화한다. */
    public Long verify(String challengeId, String code) {
        String storedCode = redisTemplate.opsForValue().get(codeKey(challengeId));
        String storedUser = redisTemplate.opsForValue().get(userKey(challengeId));

        if (storedCode == null || storedUser == null || !storedCode.equals(code)) {
            registerFailure(challengeId);
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH);
        }

        redisTemplate.delete(codeKey(challengeId));
        redisTemplate.delete(userKey(challengeId));
        redisTemplate.delete(failKey(challengeId));
        return Long.valueOf(storedUser);
    }

    // 실패 카운터 증가 (첫 실패에만 코드 TTL과 동일한 창을 연다). 5회 도달 시 코드·유저 키를 삭제해
    // 이후 시도는 storedCode == null 로 막힌다 — brute-force 차단. (EmailVerificationServiceImpl과 동일 패턴)
    private void registerFailure(String challengeId) {
        Long count = redisTemplate.opsForValue().increment(failKey(challengeId));
        if (count != null && count == 1L) {
            redisTemplate.expire(failKey(challengeId), CODE_TTL);
        }
        if (count != null && count >= MAX_VERIFY_ATTEMPTS) {
            redisTemplate.delete(codeKey(challengeId));
            redisTemplate.delete(userKey(challengeId));
        }
    }
}
