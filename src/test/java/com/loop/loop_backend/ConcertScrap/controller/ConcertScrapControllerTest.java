package com.loop.loop_backend.ConcertScrap.controller;

import com.loop.loop_backend.Concert.dto.ConcertPeriod;
import com.loop.loop_backend.Concert.dto.ConcertSummaryDto;
import com.loop.loop_backend.ConcertScrap.service.ConcertScrapService;
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

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 로그인 필수 여부는 SecurityConfig(anyRequest().authenticated())가 보장하므로 여기서는 다루지 않는다
// (standalone MockMvc라 보안 필터가 없음). 여기서는 로그인 유저 기준의 HTTP 계약을 검증한다.
class ConcertScrapControllerTest {

    private static final Long USER_ID = 1L;
    private static final Long CONCERT_ID = 10L;

    private MockMvc mockMvc;
    private ConcertScrapService concertScrapService;

    @BeforeEach
    void setUp() {
        concertScrapService = mock(ConcertScrapService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new ConcertScrapController(concertScrapService))
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

    // ── POST /api/users/me/scraps/{concertId} ─────────────────────────────

    @Test
    void 스크랩하면_200을_반환하고_로그인_유저로_스크랩한다() throws Exception {
        mockMvc.perform(post("/api/users/me/scraps/{concertId}", CONCERT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(concertScrapService).scrap(USER_ID, CONCERT_ID);
    }

    @Test
    void 없는_공연을_스크랩하면_404를_반환한다() throws Exception {
        doThrow(new BusinessException(ErrorCode.CONCERT_NOT_FOUND))
                .when(concertScrapService).scrap(USER_ID, CONCERT_ID);

        mockMvc.perform(post("/api/users/me/scraps/{concertId}", CONCERT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ── DELETE /api/users/me/scraps/{concertId} ──────────────────────────

    @Test
    void 스크랩을_취소하면_200을_반환하고_로그인_유저의_스크랩을_취소한다() throws Exception {
        mockMvc.perform(delete("/api/users/me/scraps/{concertId}", CONCERT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(concertScrapService).unscrap(USER_ID, CONCERT_ID);
    }

    // ── GET /api/users/me/scraps?period= ─────────────────────────────────

    @Test
    void 예정_스크랩_목록을_조회하면_200과_목록을_반환한다() throws Exception {
        when(concertScrapService.getMyScraps(USER_ID, ConcertPeriod.UPCOMING)).thenReturn(List.of(
                ConcertSummaryDto.builder().concertId(CONCERT_ID).title("예정 공연").build()));

        mockMvc.perform(get("/api/users/me/scraps").param("period", "UPCOMING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].concertId").value(CONCERT_ID))
                .andExpect(jsonPath("$.data[0].title").value("예정 공연"));
    }

    @Test
    void 지난_스크랩_목록을_조회하면_200과_목록을_반환한다() throws Exception {
        when(concertScrapService.getMyScraps(USER_ID, ConcertPeriod.PAST)).thenReturn(List.of(
                ConcertSummaryDto.builder().concertId(CONCERT_ID).title("지난 공연").build()));

        mockMvc.perform(get("/api/users/me/scraps").param("period", "PAST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("지난 공연"));
    }

    @Test
    void period_없이_목록을_조회하면_400이고_서비스는_호출되지_않는다() throws Exception {
        mockMvc.perform(get("/api/users/me/scraps"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(concertScrapService);
    }

    @Test
    void 잘못된_period로_목록을_조회하면_400이고_서비스는_호출되지_않는다() throws Exception {
        mockMvc.perform(get("/api/users/me/scraps").param("period", "ALL"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(concertScrapService);
    }
}