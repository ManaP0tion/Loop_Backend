package com.loop.loop_backend.User.controller;

import com.loop.loop_backend.User.service.EmailVerificationService;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import com.loop.loop_backend.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmailVerificationControllerTest {

    private static final Long USER_ID = 1L;

    private MockMvc mockMvc;
    private EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        emailVerificationService = mock(EmailVerificationService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new EmailVerificationController(emailVerificationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null, Collections.emptyList()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── POST /verification-code ──────────────────────────────────────────

    @Test
    void 인증코드_발송_요청시_200과_성공_메시지를_반환한다() throws Exception {
        mockMvc.perform(post("/api/users/me/email/verification-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(emailVerificationService).sendCode(USER_ID, "user@example.com");
    }

    @Test
    void 이메일_형식이_올바르지_않으면_400을_반환하고_서비스는_호출되지_않는다() throws Exception {
        mockMvc.perform(post("/api/users/me/email/verification-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void 메일_발송_실패시_500을_반환한다() throws Exception {
        doThrow(new BusinessException(ErrorCode.EMAIL_SEND_FAILED))
                .when(emailVerificationService).sendCode(USER_ID, "user@example.com");

        mockMvc.perform(post("/api/users/me/email/verification-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500));
    }

    // ── POST /verify ─────────────────────────────────────────────────────

    @Test
    void 인증코드_확인_성공시_200을_반환한다() throws Exception {
        mockMvc.perform(post("/api/users/me/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(emailVerificationService).verifyCode(USER_ID, "user@example.com", "123456");
    }

    @Test
    void 인증코드가_틀리면_400을_반환한다() throws Exception {
        doThrow(new BusinessException(ErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH))
                .when(emailVerificationService).verifyCode(USER_ID, "user@example.com", "000000");

        mockMvc.perform(post("/api/users/me/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void 코드가_비어있으면_400을_반환하고_서비스는_호출되지_않는다() throws Exception {
        mockMvc.perform(post("/api/users/me/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"code\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(emailVerificationService);
    }
}