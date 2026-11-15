package com.loop.loop_backend.AiJob.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * AI 워커 인증. X-API-Key 헤더가 설정된 키와 같으면 ROLE_AI_WORKER로 인증한다.
 * 틀리거나 없으면 아무것도 하지 않고, 워커 전용 체인의 권한 검사가 401을 준다.
 *
 * <p>Bean(@Component)으로 만들지 않는다 - Bean이면 Spring Boot가 모든 요청에 거는 서블릿 필터로도 등록해 버린다.
 * SecurityConfig가 직접 만들어 /api/internal/** 체인에만 넣는다.
 *
 * <p>설정된 키가 비어 있으면 어떤 요청도 인증하지 않는다(설정 누락 시 열리지 않고 닫힌다).
 * 비교는 양쪽을 SHA-256으로 해시한 뒤 MessageDigest.isEqual로 한다 - 비교 시간이나 길이로 키를 추측하지 못하게.
 */
@Slf4j
public class AiWorkerApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    public static final String ROLE = "AI_WORKER";

    private final byte[] expectedHash; // null이면 키 미설정 - 항상 거절

    public AiWorkerApiKeyFilter(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            this.expectedHash = null;
            log.warn("[AiJob] ai-worker.api-key(AI_WORKER_API_KEY)가 비어 있어 AI 워커 API는 모두 401입니다.");
        } else {
            // .env 등에 실수로 붙은 앞뒤 공백 때문에 맞는 키가 거절되지 않게
            this.expectedHash = sha256(apiKey.strip());
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (expectedHash != null && provided != null && MessageDigest.isEqual(expectedHash, sha256(provided))) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "ai-worker", null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE))));
        }
        filterChain.doFilter(request, response);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다.", e);
        }
    }
}