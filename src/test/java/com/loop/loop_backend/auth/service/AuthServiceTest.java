package com.loop.loop_backend.auth.service;

import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.auth.dto.LoginRequestDto;
import com.loop.loop_backend.auth.dto.TokenResponseDto;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import com.loop.loop_backend.common.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock LoginAttemptService loginAttemptService;
    @Mock RefreshTokenService refreshTokenService;
    @Mock TokenBlacklistService tokenBlacklistService;
    @InjectMocks AuthService authService;

    private static final String USER_ID = "testuser";
    private static final String RAW_PASSWORD = "password123!";
    private static final String ENCODED_PASSWORD = "encoded_password";

    private User emailUser() {
        User user = User.builder()
                .authProvider(AuthProvider.EMAIL)
                .status(Status.ACTIVE)
                .onboardingCompleted(true)
                .build();
        user.changePassword(ENCODED_PASSWORD);
        return user;
    }

    private User kakaoUser() {
        return User.builder()
                .authProvider(AuthProvider.KAKAO)
                .providerId("kakao-id")
                .status(Status.ACTIVE)
                .onboardingCompleted(true)
                .build();
    }

    // ── login ──────────────────────────────────────────────────────────────────

    @Test
    void 잠긴_계정은_LOGIN_LOCKED_예외를_던진다() {
        when(loginAttemptService.isLocked(USER_ID)).thenReturn(true);

        LoginRequestDto dto = mockLoginRequest(USER_ID, RAW_PASSWORD);

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.LOGIN_LOCKED);
    }

    @Test
    void 존재하지_않는_유저는_INVALID_CREDENTIALS_예외를_던진다() {
        when(loginAttemptService.isLocked(USER_ID)).thenReturn(false);
        when(userRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        LoginRequestDto dto = mockLoginRequest(USER_ID, RAW_PASSWORD);

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void 소셜_유저는_이메일_로그인_시_INVALID_CREDENTIALS_예외를_던진다() {
        when(loginAttemptService.isLocked(USER_ID)).thenReturn(false);
        when(userRepository.findByUserId(USER_ID)).thenReturn(Optional.of(kakaoUser()));

        LoginRequestDto dto = mockLoginRequest(USER_ID, RAW_PASSWORD);

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void 비밀번호_불일치시_INVALID_CREDENTIALS_예외_및_실패_횟수가_증가한다() {
        when(loginAttemptService.isLocked(USER_ID)).thenReturn(false);
        when(userRepository.findByUserId(USER_ID)).thenReturn(Optional.of(emailUser()));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

        LoginRequestDto dto = mockLoginRequest(USER_ID, RAW_PASSWORD);

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(loginAttemptService).increaseFailCount(USER_ID);
    }

    @Test
    void 로그인_성공시_토큰을_반환하고_실패_횟수를_초기화한다() {
        when(loginAttemptService.isLocked(USER_ID)).thenReturn(false);
        when(userRepository.findByUserId(USER_ID)).thenReturn(Optional.of(emailUser()));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(any())).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(any())).thenReturn("refresh-token");

        LoginRequestDto dto = mockLoginRequest(USER_ID, RAW_PASSWORD);
        TokenResponseDto result = authService.login(dto);

        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
        verify(loginAttemptService).resetFailCount(USER_ID);
        verify(refreshTokenService).save(any(), eq("refresh-token"));
    }

    // ── reissue ────────────────────────────────────────────────────────────────

    @Test
    void 유효하지_않은_Refresh_Token이면_INVALID_REFRESH_TOKEN_예외를_던진다() {
        when(jwtTokenProvider.validateRefreshToken("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.reissue("bad-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void Redis와_불일치하는_토큰이면_INVALID_REFRESH_TOKEN_예외를_던진다() {
        when(jwtTokenProvider.validateRefreshToken("old-token")).thenReturn(true);
        when(jwtTokenProvider.getUserId("old-token")).thenReturn(1L);
        when(refreshTokenService.isValid(1L, "old-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.reissue("old-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void 토큰_재발급_성공시_새_토큰을_반환하고_Redis를_갱신한다() {
        when(jwtTokenProvider.validateRefreshToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUserId("valid-token")).thenReturn(1L);
        when(refreshTokenService.isValid(1L, "valid-token")).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(1L)).thenReturn("new-access");
        when(jwtTokenProvider.createRefreshToken(1L)).thenReturn("new-refresh");

        TokenResponseDto result = authService.reissue("valid-token");

        assertThat(result.getAccessToken()).isEqualTo("new-access");
        assertThat(result.getRefreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenService).save(1L, "new-refresh");
    }

    // ── logout ─────────────────────────────────────────────────────────────────

    @Test
    void 로그아웃시_Refresh_Token삭제하고_Access_Token을_블랙리스트에_등록한다() {
        when(jwtTokenProvider.getRemainingMillis("access-token")).thenReturn(60000L);

        authService.logout(1L, "access-token");

        verify(refreshTokenService).delete(1L);
        verify(tokenBlacklistService).blacklist("access-token", 60000L);
    }

    @Test
    void Access_Token이_없으면_블랙리스트에_등록하지_않는다() {
        authService.logout(1L, null);

        verify(refreshTokenService).delete(1L);
        verify(tokenBlacklistService, never()).blacklist(any(), anyLong());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private LoginRequestDto mockLoginRequest(String userId, String password) {
        LoginRequestDto dto = mock(LoginRequestDto.class);
        when(dto.getUserId()).thenReturn(userId);
        when(dto.getPassword()).thenReturn(password);
        return dto;
    }
}