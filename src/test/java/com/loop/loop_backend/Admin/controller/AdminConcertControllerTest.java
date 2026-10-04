package com.loop.loop_backend.Admin.controller;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.loop.loop_backend.Admin.service.AdminAccessLogService;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.dto.admin.AdminConcertCreateRequest;
import com.loop.loop_backend.Concert.dto.admin.AdminConcertDetailResponse;
import com.loop.loop_backend.Concert.service.AdminConcertService;
import com.loop.loop_backend.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 관리자 공연 API 요청·응답 요구사항:
// - 등록은 유형·공연명이 필수, 아티스트는 지금 최대 1명. 어긋나면 필드 구분 없이 400이고 서비스까지 가지 않는다.
// - 수정은 공연명을 빈 값으로 비울 수 없다(null은 변경 없음이라 허용).
// - 공연 시각은 DAY 순서대로 시각만 보낸다(["18:00", null]). 응답의 시각은 HH:mm.
// - 도메인 규칙 위반(공개 필수값 누락 등)도 400 하나로 응답한다.
class AdminConcertControllerTest {

    private MockMvc mockMvc;
    private AdminConcertService service;

    private static final AdminConcertDetailResponse DETAIL = new AdminConcertDetailResponse(
            1L, null, ConcertCategory.J_POP_ARTIST, "YUURI LIVE", List.of(), null,
            LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 6),
            List.of(new AdminConcertDetailResponse.Showtime(1, LocalDate.of(2026, 12, 5), LocalTime.of(18, 0)),
                    new AdminConcertDetailResponse.Showtime(2, LocalDate.of(2026, 12, 6), null)),
            null, List.of(), false, null, null, false, null, List.of(), List.of(), List.of());

    @BeforeEach
    void setUp() {
        service = mock(AdminConcertService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminConcertController(service, mock(AdminAccessLogService.class)))
                // 앱(Spring Boot 자동 설정)과 같은 Jackson 설정 - 날짜를 배열이 아니라 "2026-12-05" 문자열로 쓴다
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    // ---------- 등록 ----------

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "{\"title\": \"YUURI LIVE\"}",                                                    // 유형 없음
            "{\"category\": \"J_POP_ARTIST\"}",                                               // 공연명 없음
            "{\"category\": \"J_POP_ARTIST\", \"title\": \"  \"}",                            // 공연명 공백
            "{\"category\": \"J_POP_ARTIST\", \"title\": \"YUURI LIVE\", \"artistIds\": [1, 2]}", // 아티스트 2명
    })
    void 등록할_때_필수값이_없거나_아티스트가_2명_이상이면_400이고_서비스를_부르지_않는다(String body) throws Exception {
        mockMvc.perform(post("/api/admin/concerts").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(service);
    }

    @Test
    void 공연_시각은_DAY_순서대로_시각만_보내고_미정은_null이다() throws Exception {
        when(service.create(any())).thenReturn(DETAIL);

        mockMvc.perform(post("/api/admin/concerts").contentType(MediaType.APPLICATION_JSON).content("""
                        {"category": "J_POP_ARTIST", "title": "YUURI LIVE",
                         "startDate": "2026-12-05", "endDate": "2026-12-06", "showtimes": ["18:00", null]}
                        """))
                .andExpect(status().isOk());

        ArgumentCaptor<AdminConcertCreateRequest> captor = ArgumentCaptor.forClass(AdminConcertCreateRequest.class);
        verify(service).create(captor.capture());
        assertThat(captor.getValue().showtimes()).containsExactly(LocalTime.of(18, 0), null);
    }

    @Test
    void 응답의_공연_시각은_DAY_날짜_HH_mm_형식이고_미정은_null이다() throws Exception {
        when(service.get(1L)).thenReturn(DETAIL);

        mockMvc.perform(get("/api/admin/concerts/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.showtimes[0].day").value(1))
                .andExpect(jsonPath("$.data.showtimes[0].date").value("2026-12-05"))
                .andExpect(jsonPath("$.data.showtimes[0].startTime").value("18:00"))
                .andExpect(jsonPath("$.data.showtimes[1].startTime").isEmpty());
    }

    // ---------- 수정 ----------

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "{\"title\": \"\"}",              // 공연명 비움
            "{\"title\": \"  \"}",
            "{\"artistIds\": [1, 2]}",        // 아티스트 2명
    })
    void 수정할_때_공연명을_비우거나_아티스트가_2명_이상이면_400이고_서비스를_부르지_않는다(String body) throws Exception {
        mockMvc.perform(patch("/api/admin/concerts/{id}", 1L).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void 아무것도_보내지_않거나_null로_보내는_것은_허용된다() throws Exception {
        when(service.update(eq(1L), any())).thenReturn(DETAIL);

        mockMvc.perform(patch("/api/admin/concerts/{id}", 1L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": null, \"published\": null}"))
                .andExpect(status().isOk());
    }

    @Test
    void 공개_필수값_누락_같은_규칙_위반은_400이다() throws Exception {
        when(service.update(eq(1L), any())).thenThrow(new IllegalArgumentException("공개하려면 필수값을 모두 채워야 한다"));

        mockMvc.perform(patch("/api/admin/concerts/{id}", 1L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"published\": true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}