package com.loop.loop_backend.Admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loop.loop_backend.Admin.service.AdminAccessLogService;
import com.loop.loop_backend.Setlist.domain.SetlistType;
import com.loop.loop_backend.Setlist.dto.SetlistResponse;
import com.loop.loop_backend.Setlist.dto.SetlistSaveRequest;
import com.loop.loop_backend.Setlist.dto.SetlistSongResponse;
import com.loop.loop_backend.Setlist.service.SetlistService;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import com.loop.loop_backend.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 관리자 셋리스트 API 요청·응답 요구사항(AD-07):
// - songIds가 없거나 비었거나 type이 틀리면 400이고 서비스까지 가지 않는다.
// - 응답은 항상 셋리스트 전체 목록. 없는 셋리스트 404, 도메인 규칙 위반 400.
// - 저장·삭제는 관리자 접근 기록을 남긴다.
class AdminSetlistControllerTest {

    private static final String BASE = "/api/admin/concerts/{concertId}/setlists";
    private static final List<SetlistResponse> LIST = List.of(new SetlistResponse(
            5L, SetlistType.RECENT, "Arena Tour", LocalDate.of(2026, 5, 1), "Zepp Haneda", 2,
            List.of(new SetlistSongResponse(1, 12L, "ドライフラワー", "드라이플라워", "https://img/a.jpg"),
                    new SetlistSongResponse(2, 7L, "ベテルギウス", null, null))));

    private MockMvc mockMvc;
    private SetlistService service;
    private AdminAccessLogService accessLog;

    @BeforeEach
    void setUp() {
        service = mock(SetlistService.class);
        accessLog = mock(AdminAccessLogService.class);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminSetlistController(service, accessLog))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    void 목록은_헤더와_곡_필드를_모두_준다() throws Exception {
        when(service.list(1L)).thenReturn(LIST);

        mockMvc.perform(get(BASE, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].setlistId").value(5))
                .andExpect(jsonPath("$.data[0].type").value("RECENT"))
                .andExpect(jsonPath("$.data[0].tourName").value("Arena Tour"))
                .andExpect(jsonPath("$.data[0].performedOn").value("2026-05-01"))
                .andExpect(jsonPath("$.data[0].venueName").value("Zepp Haneda"))
                .andExpect(jsonPath("$.data[0].songCount").value(2))
                .andExpect(jsonPath("$.data[0].songs[0].position").value(1))
                .andExpect(jsonPath("$.data[0].songs[0].songId").value(12))
                .andExpect(jsonPath("$.data[0].songs[0].titleOriginal").value("ドライフラワー"))
                .andExpect(jsonPath("$.data[0].songs[0].titleKo").value("드라이플라워"))
                .andExpect(jsonPath("$.data[0].songs[0].albumArtUrl").value("https://img/a.jpg"));
    }

    @Test
    void 저장은_요청을_그대로_넘기고_기록을_남긴다() throws Exception {
        when(service.save(eq(1L), eq(SetlistType.RECENT), any())).thenReturn(LIST);

        mockMvc.perform(put(BASE + "/RECENT", 1L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tourName\":\"Arena Tour\",\"performedOn\":\"2026-05-01\",\"venueName\":\"Zepp\",\"songIds\":[12,7,12]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].setlistId").value(5));

        ArgumentCaptor<SetlistSaveRequest> captor = ArgumentCaptor.forClass(SetlistSaveRequest.class);
        verify(service).save(eq(1L), eq(SetlistType.RECENT), captor.capture());
        assertThat(captor.getValue().songIds()).containsExactly(12L, 7L, 12L);
        assertThat(captor.getValue().performedOn()).isEqualTo(LocalDate.of(2026, 5, 1));
        verify(accessLog).log(any(), any(), eq("SAVE_SETLIST"), eq("CONCERT"), eq(1L), anyString());
    }

    @Test
    void songIds가_없거나_비었으면_400이고_서비스는_호출되지_않는다() throws Exception {
        mockMvc.perform(put(BASE + "/ACTUAL", 1L).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(BASE + "/ACTUAL", 1L).contentType(MediaType.APPLICATION_JSON).content("{\"songIds\":[]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(BASE + "/ACTUAL", 1L).contentType(MediaType.APPLICATION_JSON).content("{\"songIds\":[null]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void 잘못된_type은_400이고_서비스는_호출되지_않는다() throws Exception {
        mockMvc.perform(put(BASE + "/UNKNOWN", 1L).contentType(MediaType.APPLICATION_JSON).content("{\"songIds\":[1]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete(BASE + "/actual", 1L))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void 도메인_규칙_위반은_400() throws Exception {
        when(service.save(eq(1L), eq(SetlistType.ACTUAL), any()))
                .thenThrow(new IllegalArgumentException("공연 아티스트의 곡이 아니다"));

        mockMvc.perform(put(BASE + "/ACTUAL", 1L).contentType(MediaType.APPLICATION_JSON).content("{\"songIds\":[1]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(accessLog);
    }

    @Test
    void 삭제는_기록을_남기고_없으면_404() throws Exception {
        when(service.delete(1L, SetlistType.RECENT)).thenReturn(List.of());
        when(service.delete(1L, SetlistType.PREVIOUS_VISIT)).thenThrow(new BusinessException(ErrorCode.SETLIST_NOT_FOUND));
        when(service.delete(1L, SetlistType.ACTUAL)).thenThrow(new IllegalArgumentException("실제 셋리스트는 삭제할 수 없다"));

        mockMvc.perform(delete(BASE + "/RECENT", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        verify(accessLog).log(any(), any(), eq("DELETE_SETLIST"), eq("CONCERT"), eq(1L), anyString());

        mockMvc.perform(delete(BASE + "/PREVIOUS_VISIT", 1L))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(BASE + "/ACTUAL", 1L))
                .andExpect(status().isBadRequest());
    }
}
