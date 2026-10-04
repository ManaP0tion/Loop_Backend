package com.loop.loop_backend.Admin.controller;

import com.loop.loop_backend.Admin.service.AdminAccessLogService;
import com.loop.loop_backend.Venue.dto.VenueResponseDto;
import com.loop.loop_backend.Venue.dto.VenueUpdateRequestDto;
import com.loop.loop_backend.Venue.service.VenueService;
import com.loop.loop_backend.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 공연장 요청 검증 요구사항:
// - 등록: 필수값(공연장명·주소)이 없거나 비었거나 수용 인원이 음수면 필드 구분 없이 400 하나로 거절하고 서비스까지 가지 않는다.
// - 부분 수정: 다른 PATCH API와 같이 null(또는 필드 없음)은 변경 없음이라 허용된다.
//   필수값(공연장명·주소)은 비울 수 없어 빈 값·공백으로 보내면 400이다. 링크는 빈 문자열로 보내 비울 수 있다.
// (관리자 권한은 SecurityConfig의 /api/admin/** 규칙이 맡는다)
class AdminVenueControllerTest {

    private static final VenueResponseDto SAVED =
            new VenueResponseDto(1L, "KSPO DOME", "서울특별시 송파구", null, null, null, null, null, null, null, null, 0L);

    private MockMvc mockMvc;
    private VenueService venueService;

    @BeforeEach
    void setUp() {
        venueService = mock(VenueService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminVenueController(venueService, mock(AdminAccessLogService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    // ---------- 등록 ----------

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "{\"address\": \"서울특별시 송파구\"}",                                  // 공연장명 없음
            "{\"name\": \"  \", \"address\": \"서울특별시 송파구\"}",                 // 공연장명 공백
            "{\"name\": \"KSPO DOME\"}",                                          // 주소 없음
            "{\"name\": \"KSPO DOME\", \"address\": \"\"}",                       // 주소 빈 값
            "{\"name\": \"KSPO DOME\", \"address\": \"서울특별시 송파구\", \"capacity\": -1}", // 수용 인원 음수
    })
    void 등록할_때_필수값이_없거나_수용_인원이_음수면_400이고_서비스를_부르지_않는다(String body) throws Exception {
        mockMvc.perform(post("/api/admin/venues").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(venueService);
    }

    @Test
    void 공연장명과_주소만_있으면_등록된다() throws Exception {
        when(venueService.create(any())).thenReturn(SAVED);

        mockMvc.perform(post("/api/admin/venues").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"KSPO DOME\", \"address\": \"서울특별시 송파구\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("KSPO DOME"));
    }

    // ---------- 부분 수정 ----------

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "{\"name\": \"\"}",          // 필수값을 빈 값으로 비움
            "{\"name\": \"  \"}",        // 필수값을 공백으로 비움
            "{\"address\": \"\"}",
            "{\"address\": \"  \"}",
            "{\"capacity\": -1}",        // 수용 인원 음수
    })
    void 수정할_때_필수값을_빈_값으로_보내거나_수용_인원이_음수면_400이고_서비스를_부르지_않는다(String body) throws Exception {
        mockMvc.perform(patch("/api/admin/venues/{id}", 1L).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(venueService);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "{}",                                            // 아무것도 보내지 않음
            "{\"name\": null, \"address\": null}",           // 필수값을 null로 보냄 = 변경 없음
            "{\"seatViewUrl\": \"\", \"naverMapUrl\": \"\"}", // 링크를 빈 문자열로 비움
            "{\"capacity\": null}",                          // 수용 인원 null = 변경 없음
    })
    void 필수값을_null로_보내거나_보내지_않거나_링크를_빈_값으로_보내는_것은_허용된다(String body) throws Exception {
        when(venueService.update(eq(1L), any())).thenReturn(SAVED);

        mockMvc.perform(patch("/api/admin/venues/{id}", 1L).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        verify(venueService).update(eq(1L), any(VenueUpdateRequestDto.class));
    }
}
