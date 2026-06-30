package com.loop.loop_backend.global.jwt;

import com.loop.loop_backend.common.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private final JwtTokenProvider jwtTokenProvider =
            new JwtTokenProvider(
                    "test-secret-key-for-jwt-testing-purpose-only-needs-to-be-long-enough",
                    1800000L,       // access 30분
                    1209600000L     // refresh 14일
            );

    @Test
    void 토큰을_생성하고_userId를_추출할_수_있다() {
        // given
        Long userId = 1L;

        // when
        String accessToken = jwtTokenProvider.createAccessToken(userId);

        // then
        assertThat(accessToken).isNotBlank();
        assertThat(jwtTokenProvider.getUserId(accessToken)).isEqualTo(userId);
    }

    @Test
    void 정상_토큰은_유효성_검증을_통과한다() {
        String token = jwtTokenProvider.createAccessToken(1L);

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    void 위조된_토큰은_검증에_실패한다() {
        String token = jwtTokenProvider.createAccessToken(1L);
        String tamperedToken = token + "tampered";

        assertThat(jwtTokenProvider.validateToken(tamperedToken)).isFalse();
    }

    @Test
    void refresh_토큰도_정상적으로_생성된다() {
        String refreshToken = jwtTokenProvider.createRefreshToken(1L);

        assertThat(refreshToken).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(refreshToken)).isTrue();
    }
}
