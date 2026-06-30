package com.loop.loop_backend.auth.service;

import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.auth.dto.LoginRequestDto;
import com.loop.loop_backend.auth.dto.RefreshRequestDto;
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

        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenService.save(user.getId(), refreshToken);

        return new TokenResponseDto(accessToken, refreshToken);
    }

    public TokenResponseDto reissue(RefreshRequestDto requestDto) {
        String refreshToken = requestDto.getRefreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);

        if (!refreshTokenService.isValid(userId, refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        String newAccessToken = jwtTokenProvider.createAccessToken(userId);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);
        refreshTokenService.save(userId, newRefreshToken);

        return new TokenResponseDto(newAccessToken, newRefreshToken);
    }

    public void logout(Long userId) {
        refreshTokenService.delete(userId);
    }
}