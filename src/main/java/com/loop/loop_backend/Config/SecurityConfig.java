package com.loop.loop_backend.Config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.loop_backend.common.exception.CommonResponse;
import com.loop.loop_backend.common.exception.ErrorCode;
import com.loop.loop_backend.common.jwt.JwtAuthenticationFilter;
import com.loop.loop_backend.common.jwt.TokenAuthenticator;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final TokenAuthenticator tokenAuthenticator;
    private final ObjectMapper objectMapper;

    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;

    /**
     * local 전용: chat-test.html 포함 /dev/** 전부 개방.
     */
    @Bean
    @Order(1)
    @Profile("local")
    public SecurityFilterChain devToolsFilterChainLocal(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/test/**", "/dev/**")
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * dev/docker: chat-test.html 은 차단하고 admin.html 등 나머지 /dev/** 만 허용.
     */
    @Bean
    @Order(1)
    @Profile({"dev", "docker"})
    public SecurityFilterChain devToolsFilterChainRemote(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/test/**", "/dev/**")
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/dev/chat-test.html").denyAll()
                        .anyRequest().permitAll());
        return http.build();
    }

    /**
     * local 을 제외한 모든 프로필: 스웨거 문서 자체를 별도 Basic Auth로 게이트.
     * JWT/ADMIN 롤 체계와 무관한 전용 계정(swagger.username/password) 사용 —
     * 브라우저가 문서 페이지를 열 때 Authorization 헤더를 자동으로 싣지 않기 때문에
     * ADMIN 롤 체크로는 애초에 로그인 화면조차 못 띄운다.
     */
    @Bean
    @Order(0)
    @Profile("!local")
    public SecurityFilterChain swaggerFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    @Profile("!local")
    public InMemoryUserDetailsManager swaggerUserDetailsManager(
            @Value("${swagger.username}") String username,
            @Value("${swagger.password}") String password,
            PasswordEncoder passwordEncoder) {
        UserDetails swaggerUser = org.springframework.security.core.userdetails.User
                .withUsername(username)
                .password(passwordEncoder.encode(password))
                .roles("SWAGGER")
                .build();
        return new InMemoryUserDetailsManager(swaggerUser);
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/api/auth/login",
                                "/api/auth/kakao/login",
                                "/api/auth/kakao/client-id",
                                "/api/auth/kakao/2fa/verify",
                                "/api/auth/refresh",
                                "/api/users/register",
                                "/api/users/kakao",
                                "/ws/chat/**"
                        ).permitAll()
                        // 아티스트/콘서트는 조회만 열고 변경(등록·수정·삭제·KOPIS 동기화)은 관리자 전용.
                        .requestMatchers(HttpMethod.GET, "/api/artists/**").permitAll()
                        .requestMatchers("/api/artists/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/concerts/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/concerts/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/concerts/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/concerts/**").hasRole("ADMIN")
                        // 개인정보 접근 경로 — 관리자 전용 (처리방침 제10조 6항). 회원 조회는 /api/admin/users 로 이관.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        new JwtAuthenticationFilter(tokenAuthenticator, objectMapper),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> writeErrorResponse(response, ErrorCode.UNAUTHORIZED);
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> writeErrorResponse(response, ErrorCode.FORBIDDEN);
    }

    private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(CommonResponse.fail(errorCode)));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
