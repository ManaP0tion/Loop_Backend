package com.loop.loop_backend.Admin.controller;

import com.loop.loop_backend.Admin.service.AdminAccessLogService;
import com.loop.loop_backend.Concert.dto.admin.TicketSaleRequest;
import com.loop.loop_backend.Concert.dto.admin.TicketSaleResponse;
import com.loop.loop_backend.Concert.service.AdminTicketSaleService;
import com.loop.loop_backend.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 예매 블록 요청 요구사항:
// - 예매처 이름이 비어 있으면 필드 구분 없이 400이고 서비스까지 가지 않는다.
// - 수정(PUT)에서 opensAt을 null로 보내면 '지움'으로 서비스에 전달된다(PATCH의 '변경 없음'과 다름).
class AdminTicketSaleControllerTest {

    private MockMvc mockMvc;
    private AdminTicketSaleService service;

    @BeforeEach
    void setUp() {
        service = mock(AdminTicketSaleService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminTicketSaleController(service, mock(AdminAccessLogService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "{\"vendors\": [{\"url\": \"https://nol\"}]}",               // 이름 없음
            "{\"vendors\": [{\"name\": \"  \", \"url\": \"https://nol\"}]}", // 이름 공백
    })
    void 예매처_이름이_비어_있으면_추가와_수정_모두_400이고_서비스를_부르지_않는다(String body) throws Exception {
        mockMvc.perform(post("/api/admin/concerts/{c}/presales", 1L).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(put("/api/admin/concerts/{c}/general-sales/{s}", 1L, 3L).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void 수정에서_예매_일시를_null로_보내면_지움으로_전달된다() throws Exception {
        when(service.updatePresale(eq(1L), eq(3L), any())).thenReturn(new TicketSaleResponse(3L, null, List.of()));

        mockMvc.perform(put("/api/admin/concerts/{c}/presales/{p}", 1L, 3L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opensAt\": null, \"vendors\": [{\"name\": \"공식 안내\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(3));

        ArgumentCaptor<TicketSaleRequest> captor = ArgumentCaptor.forClass(TicketSaleRequest.class);
        verify(service).updatePresale(eq(1L), eq(3L), captor.capture());
        assertThat(captor.getValue().opensAt()).isNull();
        assertThat(captor.getValue().vendors()).extracting(TicketSaleRequest.Vendor::name).containsExactly("공식 안내");
    }
}