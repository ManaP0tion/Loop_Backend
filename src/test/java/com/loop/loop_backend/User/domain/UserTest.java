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

    @Test
    void 필수_약관에_모두_동의하면_agreementsCompleted가_true다() {
        User user = newUser();

        user.agreeToTerms(true, true, true, false);

        assertThat(user.isAge19Agreed()).isTrue();
        assertThat(user.isTermsAgreed()).isTrue();
        assertThat(user.isPrivacyAgreed()).isTrue();
        assertThat(user.isProfileInfoAgreed()).isFalse();
        assertThat(user.isAgreementsCompleted()).isTrue();
        assertThat(user.getAgreedAt()).isNotNull();
    }

    @Test
    void 필수_약관_중_하나라도_false면_agreementsCompleted는_false다() {
        User user = newUser();

        user.agreeToTerms(true, false, true, true);

        assertThat(user.isAgreementsCompleted()).isFalse();
    }

    @Test
    void 재가입시_약관_동의_상태도_모두_초기화된다() {
        User user = newUser();
        user.agreeToTerms(true, true, true, true);
        user.withdraw();

        user.reactivate();

        assertThat(user.isAge19Agreed()).isFalse();
        assertThat(user.isTermsAgreed()).isFalse();
        assertThat(user.isPrivacyAgreed()).isFalse();
        assertThat(user.isProfileInfoAgreed()).isFalse();
        assertThat(user.isAgreementsCompleted()).isFalse();
        assertThat(user.getAgreedAt()).isNull();
    }
}