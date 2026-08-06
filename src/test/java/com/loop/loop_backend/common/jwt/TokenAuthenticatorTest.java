package com.loop.loop_backend.common.jwt;

import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.auth.service.TokenBlacklistService;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * REST 필터와 STOMP 인터셉터가 공유하는 인증 규칙 검증.
 * 예전에 WebSocket 쪽에만 빠져 있던 검사(블랙리스트·계정 상태·토큰 타입)가 핵심.
 */
class TokenAuthenticatorTest {

    private static final Long USER_ID = 7L;

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(
            "test-secret-key-for-jwt-testing-purpose-only-needs-to-be-long-enough",
            1800000L, 1209600000L);

    private UserRepository userRepository;
    private TokenBlacklistService tokenBlacklistService;
    private TokenAuthenticator tokenAuthenticator;

    private String accessToken;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tokenBlacklistService = mock(TokenBlacklistService.class);
        tokenAuthenticator = new TokenAuthenticator(jwtTokenProvider, userRepository, tokenBlacklistService);

        accessToken = jwtTokenProvider.createAccessToken(USER_ID);
    }

    private User activeUser() {
        return User.builder().build();
    }

    private void givenUser(User user) {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    @Test
    void 정상_Access_Token이면_사용자를_반환한다() {
        givenUser(activeUser());

        assertThat(tokenAuthenticator.authenticate(accessToken)).isPresent();
    }

    @Test
    void Refresh_Token으로는_인증되지_않는다() {
        String refreshToken = jwtTokenProvider.createRefreshToken(USER_ID);

        assertThat(tokenAuthenticator.authenticate(refreshToken)).isEmpty();
    }

    @Test
    void 로그아웃으로_블랙리스트에_오른_토큰은_인증되지_않는다() {
        when(tokenBlacklistService.isBlacklisted(accessToken)).thenReturn(true);

        assertThat(tokenAuthenticator.authenticate(accessToken)).isEmpty();
    }

    @Test
    void 토큰이_없으면_인증되지_않는다() {
        assertThat(tokenAuthenticator.authenticate(null)).isEmpty();
    }

    @Test
    void 이용정지된_계정은_USER_SUSPENDED_예외를_던진다() {
        User user = activeUser();
        user.suspend(LocalDateTime.now().plusDays(3));
        givenUser(user);

        assertThatThrownBy(() -> tokenAuthenticator.authenticate(accessToken))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_SUSPENDED);
    }

    @Test
    void 정지기간이_지난_계정은_해제하고_통과시킨다() {
        User user = activeUser();
        user.suspend(LocalDateTime.now().minusDays(1));
        givenUser(user);

        assertThat(tokenAuthenticator.authenticate(accessToken)).isPresent();
        assertThat(user.isSuspensionExpired()).isFalse();
    }

    @Test
    void 탈퇴한_계정은_WITHDRAWN_USER_예외를_던진다() {
        User user = activeUser();
        user.withdraw();
        givenUser(user);

        assertThatThrownBy(() -> tokenAuthenticator.authenticate(accessToken))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.WITHDRAWN_USER);
    }

    @Test
    void 존재하지_않는_사용자의_토큰은_인증되지_않는다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThat(tokenAuthenticator.authenticate(accessToken)).isEmpty();
    }

    @Test
    void Bearer_접두어가_없으면_토큰을_추출하지_않는다() {
        assertThat(TokenAuthenticator.stripBearer("Bearer abc")).isEqualTo("abc");
        assertThat(TokenAuthenticator.stripBearer("abc")).isNull();
        assertThat(TokenAuthenticator.stripBearer(null)).isNull();
    }
}
