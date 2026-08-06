package com.loop.loop_backend.common.jwt;

import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.auth.service.TokenBlacklistService;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Access Token 하나로 "이 요청이 누구인가"를 판정하는 단일 진입점.
 * REST(JwtAuthenticationFilter)와 WebSocket(StompSubscriptionInterceptor)이
 * 서명·토큰타입·블랙리스트·계정상태를 똑같이 검사하도록 여기 모아둔다.
 */
@Component
@RequiredArgsConstructor
public class TokenAuthenticator {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * @return 인증된 사용자. 토큰이 없거나 서명/타입/블랙리스트/미존재 계정이면 empty
     * @throws BusinessException 토큰 자체는 유효하지만 계정이 이용정지(403)/탈퇴(410) 상태일 때
     */
    public Optional<User> authenticate(String token) {
        if (token == null
                || !jwtTokenProvider.validateAccessToken(token)
                || tokenBlacklistService.isBlacklisted(token)) {
            return Optional.empty();
        }

        // ponytail: DB per authenticated request. JWT role/status claim if throughput matters.
        User user = userRepository.findById(jwtTokenProvider.getUserId(token)).orElse(null);
        if (user == null) {
            return Optional.empty();
        }

        // 탈퇴 시 Refresh Token 만 지우므로, 남아있는 Access Token(최대 30분)은 여기서 막는다.
        if (user.getStatus() == Status.WITHDRAWN) {
            throw new BusinessException(ErrorCode.WITHDRAWN_USER);
        }

        if (user.getStatus() == Status.SUSPENDED) {
            if (!user.isSuspensionExpired()) {
                throw new BusinessException(ErrorCode.USER_SUSPENDED);
            }
            user.liftSuspension();
            userRepository.save(user);
        }

        return Optional.of(user);
    }

    /** "Bearer {token}" 헤더 값에서 토큰만 떼어낸다. 형식이 아니면 null. */
    public static String stripBearer(String authorizationHeader) {
        return (authorizationHeader != null && authorizationHeader.startsWith("Bearer "))
                ? authorizationHeader.substring(7)
                : null;
    }
}
