package com.loop.loop_backend.Lineup.controller;

import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.loop.loop_backend.Lineup.dto.LineupItemResponse;
import com.loop.loop_backend.Lineup.dto.LineupResponse;
import com.loop.loop_backend.Lineup.service.LineupService;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import com.loop.loop_backend.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 유저용 라인업 조회(BE-14) 응답 요구사항:
// - DAY 목록(번호 + "yyyy-MM-dd" 날짜)과 라인업 전체(노출 순서, 헤드라이너 표시)를 한 번에 준다.
// - 라인업이 없으면 빈 목록으로 200, 비공개 공연 403, 없는 공연 404.
class LineupControllerTest {

    private static final String URL = "/api/concerts/{concertId}/lineup";

    private MockMvc mockMvc;
    private LineupService service;

    @BeforeEach
    void setUp() {
        service = mock(LineupService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new LineupController(service))
                // 앱과 같은 Jackson 설정 - 날짜를 "2026-11-20" 문자열로 쓴다
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void DAY_목록과_라인업을_한_번에_준다() throws Exception {
        when(service.publicLineup(1L)).thenReturn(new LineupResponse(
                List.of(new LineupResponse.Day(1, LocalDate.of(2026, 11, 20)),
                        new LineupResponse.Day(2, LocalDate.of(2026, 11, 21))),
                List.of(new LineupItemResponse(10L, 3L, "YOASOBI", "요아소비", "https://img/y.png", ConcertCategory.J_POP_ARTIST, 2, true, 1),
                        new LineupItemResponse(11L, 4L, "Yuuri", null, null, ConcertCategory.J_POP_ARTIST, 1, false, 2))));

        mockMvc.perform(get(URL, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(2))
                .andExpect(jsonPath("$.data.days[0].day").value(1))
                .andExpect(jsonPath("$.data.days[0].date").value("2026-11-20"))
                .andExpect(jsonPath("$.data.artists[0].name").value("YOASOBI"))
                .andExpect(jsonPath("$.data.artists[0].day").value(2))
                .andExpect(jsonPath("$.data.artists[0].headliner").value(true))
                .andExpect(jsonPath("$.data.artists[1].name").value("Yuuri"))
                .andExpect(jsonPath("$.data.artists[1].nameKo").doesNotExist());
    }

    @Test
    void 라인업이_없으면_빈_목록으로_200() throws Exception {
        when(service.publicLineup(1L)).thenReturn(new LineupResponse(
                List.of(new LineupResponse.Day(1, LocalDate.of(2026, 11, 20))), List.of()));

        mockMvc.perform(get(URL, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(1))
                .andExpect(jsonPath("$.data.artists").isEmpty());
    }

    @Test
    void 비공개_공연은_403_없는_공연은_404() throws Exception {
        when(service.publicLineup(1L)).thenThrow(new BusinessException(ErrorCode.CONCERT_NOT_OPEN));
        when(service.publicLineup(9L)).thenThrow(new BusinessException(ErrorCode.CONCERT_NOT_FOUND));

        mockMvc.perform(get(URL, 1L)).andExpect(status().isForbidden());
        mockMvc.perform(get(URL, 9L)).andExpect(status().isNotFound());
    }
}
