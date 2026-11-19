package com.loop.loop_backend.AiJob.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 워커 인증(연동 약속 1절): X-API-Key가 설정 값과 같을 때만 워커로 인증한다.
 * 설정 값이 비어 있으면 어떤 요청도 인증하지 않는다(설정 누락 시 닫힌다).
 * 인증하지 않은 요청을 401로 거절하는 것은 체인(AiWorkerSecurityTest)이 맡고, 필터는 요청을 막지 않고 넘긴다.
 */
class AiWorkerApiKeyFilterTest {

    private static final String KEY = "s3cret-worker-key";

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private Authentication filter(String configuredKey, String headerValue) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/internal/ai-jobs/claim");
        if (headerValue != null) {
            request.addHeader("X-API-Key", headerValue);
        }
        MockFilterChain chain = new MockFilterChain();

        new AiWorkerApiKeyFilter(configuredKey).doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).as("필터는 요청을 막지 않고 다음으로 넘긴다").isNotNull();
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void 키가_같으면_워커로_인증한다() throws Exception {
        Authentication auth = filter(KEY, KEY);

        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_AI_WORKER");
    }

    @Test
    void 키가_없거나_다르면_인증하지_않는다() throws Exception {
        assertThat(filter(KEY, null)).isNull();
        assertThat(filter(KEY, "")).isNull();
        assertThat(filter(KEY, "wrong")).isNull();
        assertThat(filter(KEY, KEY + "x")).isNull();
        assertThat(filter(KEY, KEY.substring(1))).isNull();
    }

    @Test
    void 설정_값이_비어_있으면_어떤_키도_인증하지_않는다() throws Exception {
        assertThat(filter("", "")).isNull();
        assertThat(filter("", "anything")).isNull();
        assertThat(filter(null, "")).isNull();
        assertThat(filter("   ", "   ")).isNull();
    }

    @Test
    void 설정_값의_앞뒤_공백은_무시한다() throws Exception {
        assertThat(filter("  " + KEY + "\n", KEY)).isNotNull();
    }
}