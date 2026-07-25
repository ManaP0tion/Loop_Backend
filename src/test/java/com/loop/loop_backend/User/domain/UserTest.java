package com.loop.loop_backend.User.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    private User newUser() {
        return User.builder()
                .authProvider(AuthProvider.KAKAO)
                .providerId("kakao-1")
                .status(Status.ACTIVE)
                .onboardingCompleted(true)
                .build();
    }

    @Test
    void 이메일_인증을_하지_않은_사용자는_emailVerified가_false다() {
        User user = newUser();

        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void verifyEmail_호출_후에는_emailVerified가_true다() {
        User user = newUser();

        user.verifyEmail("user@example.com");

        assertThat(user.isEmailVerified()).isTrue();
    }
}