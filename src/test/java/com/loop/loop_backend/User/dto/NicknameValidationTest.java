package com.loop.loop_backend.User.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 닉네임 요구사항(온보딩·프로필 수정 공통): 최대 8자(v1은 5자), 한글·영문·숫자만.
// 온보딩에서는 닉네임이 선택이다(보내지 않으면 가입 때 받은 랜덤 닉네임).
class NicknameValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private boolean onboardingValid(String nicknameJson) throws Exception {
        String json = "{" + (nicknameJson == null ? "" : "\"nickname\": " + nicknameJson + ", ")
                + "\"birthDate\": \"2000-01-01\", \"gender\": \"FEMALE\"}";
        return validator.validate(mapper.readValue(json, OnboardingRequestDto.class)).isEmpty();
    }

    private boolean profileValid(String nickname) throws Exception {
        String json = "{\"nickname\": \"" + nickname + "\"}";
        return validator.validate(mapper.readValue(json, UserUpdateRequestDto.class)).isEmpty();
    }

    @Test
    @DisplayName("온보딩 닉네임은 8자까지 되고 9자는 안 된다")
    void onboarding_NicknameUpTo8Chars() throws Exception {
        assertThat(onboardingValid("\"새벽베텔기우스9\"")).isTrue();   // 8자
        assertThat(onboardingValid("\"새벽베텔기우스99\"")).isFalse(); // 9자
    }

    @Test
    @DisplayName("온보딩은 닉네임 없이도 된다")
    void onboarding_WithoutNickname_IsValid() throws Exception {
        assertThat(onboardingValid(null)).isTrue();
    }

    @Test
    @DisplayName("온보딩 닉네임을 보내면 특수문자·빈 값은 안 된다")
    void onboarding_InvalidNickname_IsRejected() throws Exception {
        assertThat(onboardingValid("\"달빛!\"")).isFalse();
        assertThat(onboardingValid("\"\"")).isFalse();
    }

    @Test
    @DisplayName("프로필 수정 닉네임도 8자까지 되고 9자는 안 된다")
    void profile_NicknameUpTo8Chars() throws Exception {
        assertThat(profileValid("새벽베텔기우스9")).isTrue();
        assertThat(profileValid("새벽베텔기우스99")).isFalse();
    }
}