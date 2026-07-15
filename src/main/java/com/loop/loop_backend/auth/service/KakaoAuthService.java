package com.loop.loop_backend.auth.service;

import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoAuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    @Value("${kakao.client-id}")
    private String clientId;

    @Value("${kakao.client-secret}")
    private String clientSecret;

    @Value("${kakao.redirect-uri}")
    private String redirectUri;

    @Value("${kakao.auth-url}")
    private String authUrl;

    @Value("${kakao.api-url}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public TokenResponseDto login(String code) {
        // 1. 인가코드 → 카카오 Access Token
        KakaoTokenResponseDto kakaoToken = getKakaoToken(code);

        // 2. 카카오 Access Token → 사용자 정보
        KakaoUserInfoDto userInfo = getKakaoUserInfo(kakaoToken.getAccessToken());

        // 3. 기존 회원이면 로그인, 없으면 신규 가입
        User user = userRepository
                .findByAuthProviderAndProviderId(AuthProvider.KAKAO, String.valueOf(userInfo.getId()))
                .orElseGet(() -> registerKakaoUser(userInfo));

        // 4. JWT 발급
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        // 5. Refresh Token Redis 저장
        refreshTokenService.save(user.getId(), refreshToken);

        return new TokenResponseDto(accessToken, refreshToken);
    }

    private KakaoTokenResponseDto getKakaoToken(String code) {
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
