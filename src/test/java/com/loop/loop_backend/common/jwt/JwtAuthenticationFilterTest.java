package com.loop.loop_backend.common.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.loop_backend.User.domain.Role;
import com.loop.loop_backend.User.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 약관 미동의 유저 차단 요구사항:
 * - 필수 약관에 동의하지 않은 유저는 동의 시트에 필요한 API만 쓸 수 있다.
 *   허용: 로그인·토큰 재발급·로그아웃(/api/auth/**), 내 정보 조회, 약관 동의, 탈퇴
 * - 그 밖의 API는 403 TERMS_NOT_AGREED("약관 동의가 필요합니다.")이고 요청이 컨트롤러까지 가지 않는다.
 * - 약관에 동의한 유저, 관리자, 토큰 없이 보낸 요청은 영향이 없다.
 */
class JwtAuthenticationFilterTest {

    private static final Long USER_ID = 7L;

    private final TokenAuthenticator tokenAuthenticator = mock(TokenAuthenticator.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokenAuthenticator, new ObjectMapper());

    @BeforeEach
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static User user(Role role, boolean agreed) {
        User user = User.builder().role(role).build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        if (agreed) user.agreeToTerms(true, true, true, false);
        return user;
    }

    private void givenToken(User user) {
        when(tokenAuthenticator.authenticate(any())).thenReturn(Optional.of(user));
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer token");
        return request;
    }

    /** 필터를 통과해 다음 단계(컨트롤러 쪽)로 넘어갔는지 */
    private static boolean passed(MockFilterChain chain) {
        return chain.getRequest() != null;
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "GET, /api/users/me",
            "PATCH, /api/users/me/agreements",
            "DELETE, /api/users/me",
            "POST, /api/auth/logout",
            "POST, /api/auth/refresh",
            "POST, /api/auth/kakao/login"
    })
    void 약관_미동의_유저도_동의_시트에_필요한_API는_쓸_수_있다(String method, String path) throws Exception {
        givenToken(user(Role.USER, false));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request(method, path), new MockHttpServletResponse(), chain);

        assertThat(passed(chain)).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(USER_ID);
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "GET, /api/concerts",
            "POST, /api/companions",
            "PUT, /api/users/me/profile",
            "PATCH, /api/users/me/onboarding",
            "GET, /api/users/me/artists"
    })
    void 약관_미동의_유저가_그_밖의_API를_부르면_403_TERMS_NOT_AGREED이다(String method, String path) throws Exception {
        givenToken(user(Role.USER, false));
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request(method, path), response, chain);

        assertThat(passed(chain)).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("약관 동의가 필요합니다.");
    }

    @Test
    void 약관에_동의한_유저는_모든_API를_쓸_수_있다() throws Exception {
        givenToken(user(Role.USER, true));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("POST", "/api/companions"), new MockHttpServletResponse(), chain);

        assertThat(passed(chain)).isTrue();
    }

    @Test
    void 관리자는_약관에_동의하지_않아도_막지_않는다() throws Exception {
        givenToken(user(Role.ADMIN, false));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("GET", "/api/admin/dashboard"), new MockHttpServletResponse(), chain);

        assertThat(passed(chain)).isTrue();
    }

    @Test
    void 토큰_없이_보낸_요청은_약관_검사를_하지_않는다() throws Exception {
        when(tokenAuthenticator.authenticate(any())).thenReturn(Optional.empty());
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/concerts"), new MockHttpServletResponse(), chain);

        assertThat(passed(chain)).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
