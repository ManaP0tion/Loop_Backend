package com.loop.loop_backend.auth;

import com.loop.loop_backend.auth.domain.SocialLoginType;
import org.springframework.core.convert.converter.Converter;
// import org.springframework.context.annotation.Configuration;

// @Configuration // OAuth 기능 비활성화
public class SocialLoginTypeConverter implements Converter<String, SocialLoginType> {
    @Override
    public SocialLoginType convert(String s) {
        return SocialLoginType.valueOf(s.toUpperCase());
    }
}