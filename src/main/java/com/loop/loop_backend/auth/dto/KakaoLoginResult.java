package com.loop.loop_backend.auth.dto;

/**
 * Kakao 로그인 서비스 결과. 일반 회원은 토큰을 바로 발급하고,
 * 관리자는 2단계 인증이 필요해 토큰 대신 challengeId만 내려준다.
 */
public record KakaoLoginResult(boolean twoFactorRequired, String challengeId, TokenResponseDto tokens) {

    public static KakaoLoginResult tokens(TokenResponseDto tokens) {
        return new KakaoLoginResult(false, null, tokens);
    }

    public static KakaoLoginResult twoFactor(String challengeId) {
        return new KakaoLoginResult(true, challengeId, null);
    }
}
