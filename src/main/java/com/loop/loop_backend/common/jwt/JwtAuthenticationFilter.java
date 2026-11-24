package com.loop.loop_backend.common.jwt;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.loop_backend.User.domain.Role;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.CommonResponse;
import com.loop.loop_backend.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenAuthenticator tokenAuthenticator;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String token = TokenAuthenticator.stripBearer(request.getHeader(HttpHeaders.AUTHORIZATION));

        try {
            Optional<User> authenticated;
            try {
                authenticated = tokenAuthenticator.authenticate(token);
            } catch (BusinessException e) {
                // 이용정지/탈퇴 계정 — 여기서 끊고 사유를 그대로 내려준다.
                writeError(response, e.getErrorCode());
                return;
            }

            // 약관 미동의 유저는 동의 시트에 필요한 API만 쓸 수 있다. 관리자는 어드민 콘솔이 막히지 않게 제외
            if (authenticated.isPresent() && !authenticated.get().isAgreementsCompleted()
                    && authenticated.get().getRole() != Role.ADMIN && !allowedBeforeAgreement(request)) {
                writeError(response, ErrorCode.TERMS_NOT_AGREED);
                return;
            }

            authenticated.ifPresent(user -> {
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(user.getId(), null, authorities));
                MDC.put("userId", String.valueOf(user.getId()));
            });

            filterChain.doFilter(request, response);
        } finally {
            // 스레드 재사용(Tomcat 요청 스레드) 시 다음 요청에 이전 userId가 새어나가지 않도록 항상 정리.
            MDC.clear();
        }
    }

    // 약관 동의 전 허용 목록. 막는 목록이 아니라 허용 목록이라 새 API는 자동으로 막힌다.
    // - /api/auth/**: 로그인·토큰 재발급·로그아웃 (토큰을 받거나 버리는 API)
    // - 내 정보 조회(시트를 띄울지 판단), 약관 동의, 탈퇴
    private static final List<String[]> ALLOWED_BEFORE_AGREEMENT = List.of(
            new String[]{"GET", "/api/users/me"},
            new String[]{"PATCH", "/api/users/me/agreements"},
            new String[]{"DELETE", "/api/users/me"});

    private static boolean allowedBeforeAgreement(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth/")) return true;
        return ALLOWED_BEFORE_AGREEMENT.stream()
                .anyMatch(e -> e[0].equals(request.getMethod()) && e[1].equals(path));
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(CommonResponse.fail(errorCode)));
    }
}
