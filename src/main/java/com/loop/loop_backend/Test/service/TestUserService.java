package com.loop.loop_backend.Test.service;

import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Role;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TestUserService {

    private static final int MAX_NICKNAME_RETRY = 5;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public record Created(User user, String loginId, String plainPassword) {}

    @Transactional
    public User createTestUser(String nickname) {
        return create(nickname, false, null, null).user();
    }

    @Transactional
    public User createTestUser(String nickname, boolean asAdmin) {
        return create(nickname, asAdmin, null, null).user();
    }

    /** 로그인 자격(userId/password) 포함 생성. 비지정 시 자동 부여. */
    @Transactional
    public Created create(String nickname, boolean asAdmin, String loginIdIn, String passwordIn) {
        String finalNickname;
        if (nickname != null && !nickname.isBlank()) {
            if (userRepository.existsByNickname(nickname)) {
                throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
            }
            finalNickname = nickname;
        } else {
            finalNickname = generateUniqueNickname();
        }

        String loginId = (loginIdIn == null || loginIdIn.isBlank()) ? generateUniqueLoginId(asAdmin) : loginIdIn;
        if (userRepository.existsByUserId(loginId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_USER_ID);
        }
        String plainPassword = (passwordIn == null || passwordIn.isBlank()) ? "test1234!" : passwordIn;

        User user = User.builder()
                .authProvider(AuthProvider.EMAIL)
                .providerId("test-" + UUID.randomUUID())
                .onboardingCompleted(true)
                .role(asAdmin ? Role.ADMIN : Role.USER)
                .build();
        user.completeOnboarding(finalNickname, null, null);
        user.assignEmailCredentials(loginId, passwordEncoder.encode(plainPassword));

        return new Created(userRepository.save(user), loginId, plainPassword);
    }

    private String generateUniqueNickname() {
        for (int i = 0; i < MAX_NICKNAME_RETRY; i++) {
            String candidate = "t" + (1000 + (int) (Math.random() * 9000));
            if (!userRepository.existsByNickname(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
    }

    private String generateUniqueLoginId(boolean asAdmin) {
        String prefix = asAdmin ? "admin" : "user";
        for (int i = 0; i < MAX_NICKNAME_RETRY; i++) {
            String candidate = prefix + (1000 + (int) (Math.random() * 9000));
            if (!userRepository.existsByUserId(candidate)) return candidate;
        }
        throw new BusinessException(ErrorCode.DUPLICATE_USER_ID);
    }
}
