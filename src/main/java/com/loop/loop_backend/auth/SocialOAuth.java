package com.loop.loop_backend.auth;

import com.loop.loop_backend.auth.domain.SocialLoginType;
import com.loop.loop_backend.auth.service.GoogleOauth;
import com.loop.loop_backend.auth.service.KakaoOauth;
import com.loop.loop_backend.auth.service.NaverOauth;

public interface SocialOauth {
    String getOauthRedirectURL();
    String requestAccessToken(String code);

    default SocialLoginType type() {
        if (this instanceof GoogleOauth) {
            return SocialLoginType.GOOGLE;
        } else if (this instanceof NaverOauth) {
            return SocialLoginType.NAVER;
        } else if (this instanceof KakaoOauth) {
            return SocialLoginType.KAKAO;
        } else {
            return null;
        }
    }
}