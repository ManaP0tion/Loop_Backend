package com.loop.loop_backend.Test.service;

import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TestUserService {

    private static final int MAX_NICKNAME_RETRY = 5;

    private final UserRepository userRepository;

    @Transactional
    public User createTestUser(String nickname) {
        String finalNickname;
        if (nickname != null && !nickname.isBlank()) {
            if (userRepository.existsByNickname(nickname)) {
                throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
            }
            finalNickname = nickname;
        } else {
            finalNickname = generateUniqueNickname();
        }

        User user = User.builder()
                .authProvider(AuthProvider.EMAIL)
                .providerId("test-" + UUID.randomUUID())
                .onboardingCompleted(true)
                .build();
        user.completeOnboarding(finalNickname, null, null);

        return userRepository.save(user);
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
}