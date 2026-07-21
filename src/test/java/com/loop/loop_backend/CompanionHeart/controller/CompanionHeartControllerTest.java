package com.loop.loop_backend.CompanionHeart.controller;

import com.loop.loop_backend.CompanionHeart.dto.HeartedConcertDetailDto;
import com.loop.loop_backend.CompanionHeart.dto.HeartedConcertSummaryDto;
import com.loop.loop_backend.CompanionHeart.service.CompanionHeartService;
import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import com.loop.loop_backend.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CompanionHeartControllerTest {

    private static final Long USER_ID = 1L;

    private MockMvc mockMvc;
    private CompanionHeartService companionHeartService;

    @BeforeEach
    void setUp() {
        companionHeartService = mock(CompanionHeartService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new CompanionHeartController(companionHeartService))
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

    // ── POST /{id}/heart ─────────────────────────────────────────────────

    @Test
    void 하트_누르기_요청시_200을_반환한다() throws Exception {
        mockMvc.perform(post("/api/companions/{id}/heart", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(companionHeartService).heartCompanion(USER_ID, 10L);
    }

    @Test
    void 본인_글에_하트_시도시_400을_반환한다() throws Exception {
        doThrow(new BusinessException(ErrorCode.SELF_HEART_NOT_ALLOWED))
                .when(companionHeartService).heartCompanion(USER_ID, 10L);

        mockMvc.perform(post("/api/companions/{id}/heart", 10L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ── DELETE /{id}/heart ───────────────────────────────────────────────

    @Test
    void 하트_취소_요청시_200을_반환한다() throws Exception {
        mockMvc.perform(delete("/api/companions/{id}/heart", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(companionHeartService).unheartCompanion(USER_ID, 10L);
    }

    // ── GET /me/hearts ───────────────────────────────────────────────────

    @Test
    void 하트탭_메인_조회시_200과_콘서트별_요약을_반환한다() throws Exception {
        when(companionHeartService.getMyHeartedConcerts(USER_ID)).thenReturn(List.of(
                new HeartedConcertSummaryDto(1L, "콘서트1", 5L, "아이유", null, null, 3, List.of())
        ));

        mockMvc.perform(get("/api/companions/me/hearts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].concertTitle").value("콘서트1"));
    }

    // ── GET /me/hearts/{concertId} ───────────────────────────────────────

    @Test
    void 하트탭_콘서트_상세_조회시_200과_day별_그룹을_반환한다() throws Exception {
        when(companionHeartService.getMyHeartedCompanionsByConcert(USER_ID, 1L))
                .thenReturn(new HeartedConcertDetailDto(1L, "콘서트1", 5L, "아이유", null, null,
                        Map.of(WatchDay.DAY1, List.of())));

        mockMvc.perform(get("/api/companions/me/hearts/{concertId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.concertTitle").value("콘서트1"));
    }

    @Test
    void 존재하지_않는_콘서트_상세_조회시_404를_반환한다() throws Exception {
        when(companionHeartService.getMyHeartedCompanionsByConcert(USER_ID, 999L))
                .thenThrow(new BusinessException(ErrorCode.CONCERT_NOT_FOUND));

        mockMvc.perform(get("/api/companions/me/hearts/{concertId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }
}