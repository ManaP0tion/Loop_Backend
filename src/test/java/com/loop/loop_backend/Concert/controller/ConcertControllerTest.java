package com.loop.loop_backend.Concert.controller;

import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.dto.ConcertResponseDto;
import com.loop.loop_backend.Concert.kopis.KopisSyncService;
import com.loop.loop_backend.Concert.service.ConcertService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 로그인한 사용자 ID(@AuthenticationPrincipal)가 실제로 각 조회 엔드포인트를 거쳐
// ConcertService에 그대로 전달되는지 검증한다. (콘서트별 companionCount가 조회자마다
// 달라야 한다는 요구사항이 컨트롤러 계층에서 끊기지 않는지 확인하는 것이 목적)
class ConcertControllerTest {

    private static final Long USER_ID = 42L;

    private MockMvc mockMvc;
    private ConcertService concertService;

    @BeforeEach
    void setUp() {
        concertService = mock(ConcertService.class);
        KopisSyncService kopisSyncService = mock(KopisSyncService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new ConcertController(concertService, kopisSyncService))
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

    private ConcertResponseDto responseWithCount(long companionCount) {
        return ConcertResponseDto.builder()
                .id(1L)
                .title("테스트 콘서트")
                .companionCount(companionCount)
                .build();
    }

    @Test
    void 콘서트_단건_조회시_로그인한_사용자ID가_서비스로_전달되고_그_기준_동행수가_반환된다() throws Exception {
        when(concertService.getConcertById(1L, USER_ID)).thenReturn(responseWithCount(3L));

        mockMvc.perform(get("/api/concerts/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companionCount").value(3));

        verify(concertService).getConcertById(1L, USER_ID);
    }

    @Test
    void 카테고리_없이_목록_조회시_로그인한_사용자ID로_getAllConcerts가_호출된다() throws Exception {
        when(concertService.getAllConcerts(USER_ID)).thenReturn(List.of(responseWithCount(5L)));

        mockMvc.perform(get("/api/concerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].companionCount").value(5));

        verify(concertService).getAllConcerts(USER_ID);
        verify(concertService, never()).getConcertsByCategory(any(), any());
    }

    @Test
    void 카테고리_지정시_로그인한_사용자ID로_getConcertsByCategory가_호출된다() throws Exception {
        when(concertService.getConcertsByCategory(ConcertCategory.DOMESTIC_ARTIST, USER_ID))
                .thenReturn(List.of(responseWithCount(1L)));

        mockMvc.perform(get("/api/concerts").param("category", "DOMESTIC_ARTIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].companionCount").value(1));

        verify(concertService).getConcertsByCategory(ConcertCategory.DOMESTIC_ARTIST, USER_ID);
    }

    @Test
    void 검색시_로그인한_사용자ID로_searchConcertsByTitle이_호출된다() throws Exception {
        when(concertService.searchConcertsByTitle("아이유", USER_ID))
                .thenReturn(List.of(responseWithCount(2L)));

        mockMvc.perform(get("/api/concerts/search").param("title", "아이유"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].companionCount").value(2));

        verify(concertService).searchConcertsByTitle("아이유", USER_ID);
    }

    @Test
    void 아티스트별_조회시_로그인한_사용자ID로_getConcertsByArtist가_호출된다() throws Exception {
        when(concertService.getConcertsByArtist(7L, USER_ID))
                .thenReturn(List.of(responseWithCount(4L)));

        mockMvc.perform(get("/api/concerts/artist/{artistId}", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].companionCount").value(4));

        verify(concertService).getConcertsByArtist(7L, USER_ID);
    }
}