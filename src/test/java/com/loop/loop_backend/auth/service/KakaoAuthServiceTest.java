package com.loop.loop_backend.auth.service;

import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.auth.dto.KakaoTokenResponseDto;
import com.loop.loop_backend.auth.dto.KakaoUserInfoDto;
import com.loop.loop_backend.auth.dto.TokenResponseDto;
import com.loop.loop_backend.common.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KakaoAuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock RefreshTokenService refreshTokenService;
    @Mock RestTemplate restTemplate;
    @InjectMocks KakaoAuthService kakaoAuthService;

    private static final String REDIRECT_URI = "https://loop.io.kr/oauth/callback";
    private static final String KAKAO_PROVIDER_ID = "99";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(kakaoAuthService, "allowedRedirectUris", new String[]{REDIRECT_URI});
        ReflectionTestUtils.setField(kakaoAuthService, "authUrl", "https://kauth.kakao.com");
        ReflectionTestUtils.setField(kakaoAuthService, "apiUrl", "https://kapi.kakao.com");
        ReflectionTestUtils.setField(kakaoAuthService, "restTemplate", restTemplate);

        // 카카오 토큰/사용자 정보 응답을 흉내낸다 (두 DTO 모두 Jackson 역직렬화 전용이라 생성자가 없어 리플렉션으로 값을 채운다)
        KakaoTokenResponseDto tokenResponse = new KakaoTokenResponseDto();
        ReflectionTestUtils.setField(tokenResponse, "accessToken", "kakao-access-token");

        KakaoUserInfoDto userInfo = new KakaoUserInfoDto();
        ReflectionTestUtils.setField(userInfo, "id", Long.valueOf(KAKAO_PROVIDER_ID));

        when(restTemplate.postForEntity(eq("https://kauth.kakao.com/oauth/token"), any(HttpEntity.class), eq(KakaoTokenResponseDto.class)))
                .thenReturn(ResponseEntity.ok(tokenResponse));
        when(restTemplate.exchange(eq("https://kapi.kakao.com/v2/user/me"), eq(HttpMethod.GET), any(HttpEntity.class), eq(KakaoUserInfoDto.class)))
                .thenReturn(ResponseEntity.ok(userInfo));

        when(jwtTokenProvider.createAccessToken(any())).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(any())).thenReturn("refresh-token");
    }

    private User existingUserWith(boolean emailVerified, boolean onboardingCompleted, long id) {
        User user = User.builder()
                .authProvider(AuthProvider.KAKAO)
                .providerId(KAKAO_PROVIDER_ID)
                .status(Status.ACTIVE)
                .onboardingCompleted(onboardingCompleted)
                .build();
        if (emailVerified) {
            user.verifyEmail("user@example.com");
        }
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void 이메일_인증만_완료하고_이탈했다가_재로그인해도_인증완료_상태는_유지되고_온보딩은_여전히_미완료다() {
        User existing = existingUserWith(true, false, 1L);
        when(userRepository.findByAuthProviderAndProviderId(AuthProvider.KAKAO, KAKAO_PROVIDER_ID))
                .thenReturn(Optional.of(existing));

        TokenResponseDto result = kakaoAuthService.login("auth-code", REDIRECT_URI);

        // 재로그인 과정에서 신규 가입 처리가 되지 않고 기존 사용자 그대로 재사용된다
        verify(userRepository, never()).save(any());
        // 발급된 토큰이 기존 사용자(id=1) 기준이다 - 새 계정이 아니라는 뜻
        verify(jwtTokenProvider).createAccessToken(1L);
        verify(jwtTokenProvider).createRefreshToken(1L);

        assertThat(existing.isEmailVerified()).isTrue();
        assertThat(existing.isOnboardingCompleted()).isFalse();
        assertThat(result.getAccessToken()).isEqualTo("access-token");
    }

    @Test
    void 탈퇴한_계정으로_재로그인하면_같은_계정이_ACTIVE로_되살아난다() {
        User withdrawn = existingUserWith(true, true, 1L);
        withdrawn.withdraw();
        when(userRepository.findByAuthProviderAndProviderId(AuthProvider.KAKAO, KAKAO_PROVIDER_ID))
                .thenReturn(Optional.of(withdrawn));

        kakaoAuthService.login("auth-code", REDIRECT_URI);

        // 새 계정을 만들지 않고 같은 계정(id=1)을 그대로 되살린다
        verify(userRepository, never()).save(any());
        assertThat(withdrawn.getStatus()).isEqualTo(Status.ACTIVE);
    }

    @Test
    void 이메일_인증도_온보딩도_안한_신규_사용자는_로그인시_가입_처리되고_두_상태_모두_false다() {
        when(userRepository.findByAuthProviderAndProviderId(AuthProvider.KAKAO, KAKAO_PROVIDER_ID))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 2L);
            return saved;
        });

        kakaoAuthService.login("auth-code", REDIRECT_URI);

        verify(userRepository).save(argThatNewUser());
    }

    private User argThatNewUser() {
        return org.mockito.ArgumentMatchers.argThat(user ->
                !user.isEmailVerified() && !user.isOnboardingCompleted());
    }
}