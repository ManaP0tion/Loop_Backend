package com.loop.loop_backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class KakaoUserInfoDto {

    private Long id;

    @JsonProperty("kakao_account")
    private KakaoAccount kakaoAccount;

    @Getter
    public static class KakaoAccount {
        private String email;

        @JsonProperty("is_email_valid")
        private Boolean emailValid;

        @JsonProperty("is_email_verified")
        private Boolean emailVerified;

        private Profile profile;

        @Getter
        public static class Profile {
            private String nickname;
        }
    }

    public String getEmail() {
        return kakaoAccount != null ? kakaoAccount.getEmail() : null;
    }

    /** 카카오가 유효·인증 완료라고 알려 준 이메일만. 아니면(동의 안 함 포함) null. */
    public String getVerifiedEmail() {
        if (kakaoAccount == null || kakaoAccount.getEmail() == null || kakaoAccount.getEmail().isBlank()) return null;
        return Boolean.TRUE.equals(kakaoAccount.getEmailValid()) && Boolean.TRUE.equals(kakaoAccount.getEmailVerified())
                ? kakaoAccount.getEmail() : null;
    }

    public String getNickname() {
        return kakaoAccount != null && kakaoAccount.getProfile() != null
                ? kakaoAccount.getProfile().getNickname() : null;
    }
}