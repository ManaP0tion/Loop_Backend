package com.loop.loop_backend.auth.service;

import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Role;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.auth.dto.KakaoLoginResult;
import com.loop.loop_backend.auth.dto.KakaoTokenResponseDto;
import com.loop.loop_backend.auth.dto.KakaoUserInfoDto;
import com.loop.loop_backend.auth.dto.TokenResponseDto;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import com.loop.loop_backend.common.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoAuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AdminTwoFactorService adminTwoFactorService;

    @Value("${kakao.client-id}")
    private String clientId;

    public String getClientId() { return clientId; }

    @Value("${kakao.client-secret}")
    private String clientSecret;

    @Value("${kakao.allowed-redirect-uris}")
    private String[] allowedRedirectUris;

    @Value("${kakao.auth-url}")
    private String authUrl;

    @Value("${kakao.api-url}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Transactional
    public KakaoLoginResult login(String code, String redirectUri) {
        // 0. 프론트가 실제로 카카오 인가 요청에 썼던 redirect_uri인지 검증 (화이트리스트 방식, 임의 주소 우회 방지)
        if (!List.of(allowedRedirectUris).contains(redirectUri)) {
            throw new BusinessException(ErrorCode.INVALID_REDIRECT_URI);
        }

        // 1. 인가코드 → 카카오 Access Token
        KakaoTokenResponseDto kakaoToken = getKakaoToken(code, redirectUri);

        // 2. 카카오 Access Token → 사용자 정보
        KakaoUserInfoDto userInfo = getKakaoUserInfo(kakaoToken.getAccessToken());

        // 3. 기존 회원이면 로그인, 없으면 신규 가입
        User user = userRepository
                .findByAuthProviderAndProviderId(AuthProvider.KAKAO, String.valueOf(userInfo.getId()))
                .orElseGet(() -> registerKakaoUser(userInfo));

        // 3-1. 탈퇴 계정으로 재가입하는 경우: 잔존 데이터는 이미 탈퇴 시점(withdrawUser)에 정리됐으므로 계정만 되살린다.
        // 차단(Block)은 탈퇴/재가입과 무관하게 유지된다.
        if (user.getStatus() == Status.WITHDRAWN) {
            user.reactivate();
        }

        // 이용정지된 계정은 로그인 차단. 기간 만료 시 즉시 해제.
        if (user.getStatus() == Status.SUSPENDED) {
            if (user.isSuspensionExpired()) user.liftSuspension();
            else throw new BusinessException(ErrorCode.USER_SUSPENDED);
        }

        // 4. 관리자는 2단계 인증으로 분기 (Kakao 로그인 경로 한정). 토큰 대신 challengeId만 내려주고,
        //    관리자 이메일로 발송된 코드를 검증해야 completeAdminLogin에서 토큰이 나온다.
        if (user.getRole() == Role.ADMIN) {
            if (user.getEmail() == null) {
                throw new BusinessException(ErrorCode.ADMIN_2FA_EMAIL_MISSING);
            }
            String challengeId = adminTwoFactorService.startChallenge(user.getId(), user.getEmail());
            return KakaoLoginResult.twoFactor(challengeId);
        }

        // 5. 일반 회원: 즉시 토큰 발급
        return KakaoLoginResult.tokens(issueTokens(user));
    }

    private TokenResponseDto issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenService.save(user.getId(), refreshToken);
        return new TokenResponseDto(accessToken, refreshToken);
    }

    /** 관리자 2단계 인증 코드 검증 완료 → 토큰 발급. */
    @Transactional
    public TokenResponseDto completeAdminLogin(String challengeId, String code) {
        Long userId = adminTwoFactorService.verify(challengeId, code);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 코드 발송~입력 사이에 상태/권한이 바뀌었을 수 있으니 방어적으로 재검사한다.
        if (user.getStatus() == Status.SUSPENDED) {
            if (user.isSuspensionExpired()) user.liftSuspension();
            else throw new BusinessException(ErrorCode.USER_SUSPENDED);
        }
        if (user.getRole() != Role.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        return issueTokens(user);
    }

    private KakaoTokenResponseDto getKakaoToken(String code, String redirectUri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("code", code);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<KakaoTokenResponseDto> response = restTemplate.postForEntity(
                    authUrl + "/oauth/token",
                    request,
                    KakaoTokenResponseDto.class
            );
            return response.getBody();
        } catch (HttpClientErrorException e) {
            // 잘못되었거나 이미 사용된/만료된 인가 코드
            log.warn("카카오 토큰 발급 실패: {}", e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.INVALID_KAKAO_CODE);
        }
    }

    private KakaoUserInfoDto getKakaoUserInfo(String kakaoAccessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(kakaoAccessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<KakaoUserInfoDto> response = restTemplate.exchange(
                apiUrl + "/v2/user/me",
                HttpMethod.GET,
                request,
                KakaoUserInfoDto.class
        );

        return response.getBody();
    }

    private User registerKakaoUser(KakaoUserInfoDto userInfo) {
        User user = User.builder()
                .authProvider(AuthProvider.KAKAO)
                .providerId(String.valueOf(userInfo.getId()))
                .onboardingCompleted(false)
                .build();
        return userRepository.save(user);
    }
}
