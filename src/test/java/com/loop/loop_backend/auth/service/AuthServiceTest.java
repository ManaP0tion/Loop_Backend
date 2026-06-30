package com.loop.loop_backend.auth.service;

import com.loop.loop_backend.User.domain.AgeGroup;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Gender;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.auth.dto.LoginRequestDto;
import com.loop.loop_backend.auth.dto.RefreshRequestDto;
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
    @InjectMocks AuthService authService;

    private static final String USER_ID = "testuser";
    private static final String RAW_PASSWORD = "password123!";
    private static final String ENCODED_PASSWORD = "encoded_password";

    private User emailUser() {
        return User.registerEmail(USER_ID, ENCODED_PASSWORD, "test@email.com",
                "닉네임", Gender.MALE, AgeGroup.AGE_20S);
    }

    private User kakaoUser() {
        return User.registerKakao("kakao-id", "test@email.com",
                "닉네임", Gender.MALE, AgeGroup.AGE_20S);
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
        when(jwtTokenProvider.validateToken("bad-token")).thenReturn(false);

        RefreshRequestDto dto = mockRefreshRequest("bad-token");

        assertThatThrownBy(() -> authService.reissue(dto))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void Redis와_불일치하는_토큰이면_INVALID_REFRESH_TOKEN_예외를_던진다() {
        when(jwtTokenProvider.validateToken("old-token")).thenReturn(true);
        when(jwtTokenProvider.getUserId("old-token")).thenReturn(1L);
        when(refreshTokenService.isValid(1L, "old-token")).thenReturn(false);

        RefreshRequestDto dto = mockRefreshRequest("old-token");

        assertThatThrownBy(() -> authService.reissue(dto))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void 토큰_재발급_성공시_새_토큰을_반환하고_Redis를_갱신한다() {
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUserId("valid-token")).thenReturn(1L);
        when(refreshTokenService.isValid(1L, "valid-token")).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(1L)).thenReturn("new-access");
        when(jwtTokenProvider.createRefreshToken(1L)).thenReturn("new-refresh");

        RefreshRequestDto dto = mockRefreshRequest("valid-token");
        TokenResponseDto result = authService.reissue(dto);

        assertThat(result.getAccessToken()).isEqualTo("new-access");
        assertThat(result.getRefreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenService).save(1L, "new-refresh");
    }

    // ── logout ─────────────────────────────────────────────────────────────────

    @Test
    void 로그아웃시_Refresh_Token이_삭제된다() {
        authService.logout(1L);

        verify(refreshTokenService).delete(1L);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private LoginRequestDto mockLoginRequest(String userId, String password) {
        LoginRequestDto dto = mock(LoginRequestDto.class);
        when(dto.getUserId()).thenReturn(userId);
        when(dto.getPassword()).thenReturn(password);
        return dto;
    }

    private RefreshRequestDto mockRefreshRequest(String token) {
        RefreshRequestDto dto = mock(RefreshRequestDto.class);
        when(dto.getRefreshToken()).thenReturn(token);
        return dto;
    }
}