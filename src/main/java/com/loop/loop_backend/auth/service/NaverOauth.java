package com.loop.loop_backend.auth.service;

import com.loop.loop_backend.auth.SocialOauth;
// import org.springframework.stereotype.Component;

// @Component // OAuth 기능 비활성화
public class NaverOauth implements SocialOauth {
    @Override
    public String getOauthRedirectURL() {
        return "";
    }

    @Override
    public String requestAccessToken(String code) {
        return null;
    }
}