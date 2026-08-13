package com.loop.loop_backend.auth.service;

import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import com.loop.loop_backend.auth.dto.LoginRequestDto;
import com.loop.loop_backend.auth.dto.TokenResponseDto;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import com.loop.loop_backend.common.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginAttemptService loginAttemptService;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;

    @Transactional
    public TokenResponseDto login(LoginRequestDto requestDto) {
        String userId = requestDto.getUserId();

        if (loginAttemptService.isLocked(userId)) {
            throw new BusinessException(ErrorCode.LOGIN_LOCKED);
        }

        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (user.getAuthProvider() != AuthProvider.EMAIL) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(requestDto.getPassword(), user.getPassword())) {
            loginAttemptService.increaseFailCount(userId);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        loginAttemptService.resetFailCount(userId);

        // 이용정지된 계정은 로그인 차단. 기간 만료 시 즉시 해제 후 통과.
        if (user.getStatus() == Status.SUSPENDED) {
            if (user.isSuspensionExpired()) user.liftSuspension();
            else throw new BusinessException(ErrorCode.USER_SUSPENDED);
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenService.save(user.getId(), refreshToken);

        return new TokenResponseDto(accessToken, refreshToken);
    }

    @Transactional
    public TokenResponseDto reissue(String refreshToken) {
        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);

        if (!refreshTokenService.isValid(userId, refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 재발급도 access token과 같은 계정상태 게이트를 통과해야 한다(TokenAuthenticator 참고).
        // 안 그러면 정지/강제탈퇴 계정이 refresh로 세션을 무한 연장한다.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (user.getStatus() == Status.WITHDRAWN) {
            throw new BusinessException(ErrorCode.WITHDRAWN_USER);
        }

        if (user.getStatus() == Status.SUSPENDED) {
            if (!user.isSuspensionExpired()) throw new BusinessException(ErrorCode.USER_SUSPENDED);
            user.liftSuspension();
        }

        String newAccessToken = jwtTokenProvider.createAccessToken(userId);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);
        refreshTokenService.save(userId, newRefreshToken);

        return new TokenResponseDto(newAccessToken, newRefreshToken);
    }

    public void logout(Long userId, String accessToken) {
        refreshTokenService.delete(userId);
        if (accessToken != null) {
            tokenBlacklistService.blacklist(accessToken, jwtTokenProvider.getRemainingMillis(accessToken));
        }
    }
}