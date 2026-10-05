package com.loop.loop_backend.Admin.controller;

import com.loop.loop_backend.Admin.service.AdminAccessLogService;
import com.loop.loop_backend.Lineup.dto.LineupAddRequest;
import com.loop.loop_backend.Lineup.dto.LineupItemResponse;
import com.loop.loop_backend.Lineup.dto.LineupPatchRequests.Direction;
import com.loop.loop_backend.Lineup.service.LineupService;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 관리자 라인업 API 요청·응답 요구사항(AD-04, BE-13):
// - 필수값(day, direction, headliner)이 없거나 형식이 틀리면 400이고 서비스까지 가지 않는다.
// - 응답은 항상 라인업 전체 목록. 중복 409, 없는 항목 404, 도메인 규칙 위반 400.
// - 추가·DAY 변경·삭제는 관리자 접근 기록을 남긴다.
class AdminLineupControllerTest {

    private static final String BASE = "/api/admin/concerts/{concertId}/lineup";
    private static final List<LineupItemResponse> LIST = List.of(
            new LineupItemResponse(10L, 3L, "YOASOBI", "요아소비", "https://img/yoasobi.png", 1, true, 1),
            new LineupItemResponse(11L, 4L, "Yuuri", null, null, 2, false, 2));

    private MockMvc mockMvc;
    private LineupService service;
    private AdminAccessLogService accessLog;

    @BeforeEach
    void setUp() {
        service = mock(LineupService.class);
        accessLog = mock(AdminAccessLogService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminLineupController(service, accessLog))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    private void verifyLogged(String action) {
        verify(accessLog).log(any(), any(), eq(action), eq("CONCERT"), eq(1L), anyString());
    }

    // ---------- 목록 ----------

    @Test
    void 목록은_노출_순서대로_항목_필드를_모두_준다() throws Exception {
        when(service.list(1L)).thenReturn(LIST);

        mockMvc.perform(get(BASE, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].lineupId").value(10))
                .andExpect(jsonPath("$.data[0].artistId").value(3))
                .andExpect(jsonPath("$.data[0].name").value("YOASOBI"))
                .andExpect(jsonPath("$.data[0].nameKo").value("요아소비"))
                .andExpect(jsonPath("$.data[0].imageUrl").value("https://img/yoasobi.png"))
                .andExpect(jsonPath("$.data[0].day").value(1))
                .andExpect(jsonPath("$.data[0].headliner").value(true))
                .andExpect(jsonPath("$.data[0].displayOrder").value(1))
                .andExpect(jsonPath("$.data[1].name").value("Yuuri"));
    }

    @Test
    void 없는_공연이면_404() throws Exception {
        when(service.list(9L)).thenThrow(new BusinessException(ErrorCode.CONCERT_NOT_FOUND));

        mockMvc.perform(get(BASE, 9L)).andExpect(status().isNotFound());
    }

    // ---------- 추가 ----------

    @Test
    void DB_선택_요청은_그대로_서비스로_전달되고_기록이_남는다() throws Exception {
        when(service.add(eq(1L), any())).thenReturn(LIST);

        mockMvc.perform(post(BASE, 1L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\": 2, \"artistIds\": [3, 7]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        ArgumentCaptor<LineupAddRequest> captor = ArgumentCaptor.forClass(LineupAddRequest.class);
        verify(service).add(eq(1L), captor.capture());
        assertThat(captor.getValue().day()).isEqualTo(2);
        assertThat(captor.getValue().artistIds()).containsExactly(3L, 7L);
        assertThat(captor.getValue().name()).isNull();
        verifyLogged("ADD_LINEUP");
    }

    @Test
    void 직접_입력_요청은_이름과_이미지가_전달된다() throws Exception {
        when(service.add(eq(1L), any())).thenReturn(LIST);

        mockMvc.perform(post(BASE, 1L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\": 1, \"name\": \"Vaundy\", \"imageUrl\": \"https://img/v.png\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<LineupAddRequest> captor = ArgumentCaptor.forClass(LineupAddRequest.class);
        verify(service).add(eq(1L), captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Vaundy");
        assertThat(captor.getValue().imageUrl()).isEqualTo("https://img/v.png");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "{\"artistIds\": [3]}",                                     // day 없음
            "{\"day\": \"DAY1\", \"artistIds\": [3]}",                  // day 형식 오류
    })
    void 추가_요청이_잘못되면_400이고_서비스를_부르지_않는다(String body) throws Exception {
        mockMvc.perform(post(BASE, 1L).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service, accessLog);
    }

    @Test
    void 직접_입력_이름이_100자를_넘으면_400이고_서비스를_부르지_않는다() throws Exception {
        mockMvc.perform(post(BASE, 1L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\": 1, \"name\": \"" + "a".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service, accessLog);
    }

    @Test
    void 같은_DAY_중복이면_409이고_기록을_남기지_않는다() throws Exception {
        when(service.add(eq(1L), any())).thenThrow(new BusinessException(ErrorCode.DUPLICATE_LINEUP));

        mockMvc.perform(post(BASE, 1L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\": 1, \"artistIds\": [3]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));

        verifyNoInteractions(accessLog);
    }

    @Test
    void 도메인_규칙_위반은_400() throws Exception {
        when(service.add(eq(1L), any())).thenThrow(new IllegalArgumentException("DAY 범위 밖"));

        mockMvc.perform(post(BASE, 1L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\": 9, \"artistIds\": [3]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ---------- DAY 변경 · 삭제 ----------

    @Test
    void DAY_변경은_서비스로_전달되고_기록이_남는다() throws Exception {
        when(service.changeDay(1L, 10L, 3)).thenReturn(LIST);

        mockMvc.perform(patch(BASE + "/{lineupId}", 1L, 10L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\": 3}"))
                .andExpect(status().isOk());

        verify(service).changeDay(1L, 10L, 3);
        verifyLogged("UPDATE_LINEUP");
    }

    @Test
    void DAY_없이_변경하면_400() throws Exception {
        mockMvc.perform(patch(BASE + "/{lineupId}", 1L, 10L).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).changeDay(anyLong(), anyLong(), anyInt());
    }

    @Test
    void 삭제는_서비스로_전달되고_기록이_남는다() throws Exception {
        when(service.delete(1L, 10L)).thenReturn(LIST.subList(1, 2));

        mockMvc.perform(delete(BASE + "/{lineupId}", 1L, 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        verifyLogged("DELETE_LINEUP");
    }

    @Test
    void 없는_라인업_항목이면_404() throws Exception {
        when(service.delete(1L, 99L)).thenThrow(new BusinessException(ErrorCode.LINEUP_NOT_FOUND));

        mockMvc.perform(delete(BASE + "/{lineupId}", 1L, 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ---------- 순서 · 헤드라이너 ----------

    @Test
    void 순서_이동_방향이_서비스로_전달된다() throws Exception {
        when(service.move(1L, 10L, Direction.DOWN)).thenReturn(LIST);

        mockMvc.perform(patch(BASE + "/{lineupId}/order", 1L, 10L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direction\": \"DOWN\"}"))
                .andExpect(status().isOk());

        verify(service).move(1L, 10L, Direction.DOWN);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{}", "{\"direction\": \"LEFT\"}"})
    void 순서_이동_방향이_없거나_틀리면_400(String body) throws Exception {
        mockMvc.perform(patch(BASE + "/{lineupId}/order", 1L, 10L).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void 헤드라이너_지정이_서비스로_전달되고_없으면_400() throws Exception {
        when(service.changeHeadliner(1L, 10L, true)).thenReturn(LIST);

        mockMvc.perform(patch(BASE + "/{lineupId}/headliner", 1L, 10L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headliner\": true}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch(BASE + "/{lineupId}/headliner", 1L, 10L).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(service, times(1)).changeHeadliner(anyLong(), anyLong(), anyBoolean());
        verify(service).changeHeadliner(1L, 10L, true);
    }
}
