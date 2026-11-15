package com.loop.loop_backend.Admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.loop_backend.Admin.service.AdminAccessLogService;
import com.loop.loop_backend.AiJob.domain.AiJobStatus;
import com.loop.loop_backend.AiJob.domain.AiJobType;
import com.loop.loop_backend.AiJob.dto.AdminAiJobCreateRequest;
import com.loop.loop_backend.AiJob.dto.AdminAiJobCreateResponse;
import com.loop.loop_backend.AiJob.dto.AdminAiJobResponse;
import com.loop.loop_backend.AiJob.service.AiJobService;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import com.loop.loop_backend.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 어드민 AI 작업 API(연동 약속 6절): 어드민은 type과 concertId만 보내고 jobId를 받는다.
 * 상태 조회는 status와 DONE이면 result(JSON 객체), FAILED면 error(code·message·retryable)를 준다.
 */
@ExtendWith(MockitoExtension.class)
class AdminAiJobControllerTest {

    @Mock AiJobService aiJobService;
    @Mock AdminAccessLogService accessLog;
    @InjectMocks AdminAiJobController controller;

    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    void 종류와_공연만_보내면_작업ID를_받는다() throws Exception {
        when(aiJobService.create(any(), any())).thenReturn(new AdminAiJobCreateResponse(123L));

        mockMvc.perform(post("/api/admin/ai-jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CONCERT_ENRICH\",\"concertId\":31}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value(123));
    }

    @Test
    void 종류나_공연이_없으면_400이고_작업을_만들지_않는다() throws Exception {
        mockMvc.perform(post("/api/admin/ai-jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CONCERT_ENRICH\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/ai-jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"concertId\":31}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(aiJobService);
    }

    @Test
    void 만들_수_없는_작업이면_400과_사유() throws Exception {
        when(aiJobService.create(any(), any(AdminAiJobCreateRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.AI_JOB_FESTIVAL_ONLY));

        mockMvc.perform(post("/api/admin/ai-jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FESTIVAL_LINEUP\",\"concertId\":31}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorCode.AI_JOB_FESTIVAL_ONLY.getMessage()));
    }

    @Test
    void 없는_공연이면_404() throws Exception {
        when(aiJobService.create(any(), any())).thenThrow(new BusinessException(ErrorCode.CONCERT_NOT_FOUND));

        mockMvc.perform(post("/api/admin/ai-jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CONCERT_ENRICH\",\"concertId\":999}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 끝난_작업은_결과를_JSON_객체_그대로_준다() throws Exception {
        when(aiJobService.get(123L)).thenReturn(new AdminAiJobResponse(
                123L, AiJobType.CONCERT_ENRICH, 31L, null, AiJobStatus.DONE,
                mapper.readTree("{\"status\":\"FOUND\",\"presales\":[]}"), null, 1,
                LocalDateTime.of(2026, 11, 20, 10, 0), LocalDateTime.of(2026, 11, 20, 10, 1)));

        mockMvc.perform(get("/api/admin/ai-jobs/{jobId}", 123L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.result.status").value("FOUND"))
                .andExpect(jsonPath("$.data.result.presales").isArray())
                .andExpect(jsonPath("$.data.createdAt").exists());
    }

    @Test
    void 실패한_작업은_코드_문구_다시_시도_가능_여부를_준다() throws Exception {
        when(aiJobService.get(124L)).thenReturn(new AdminAiJobResponse(
                124L, AiJobType.CONCERT_ENRICH, 31L, null, AiJobStatus.FAILED, null,
                new AdminAiJobResponse.Error("LEASE_EXPIRED", "AI 워커가 시간 안에 결과를 보내지 않았습니다.", true), 2,
                LocalDateTime.of(2026, 11, 20, 10, 0), LocalDateTime.of(2026, 11, 20, 10, 21)));

        mockMvc.perform(get("/api/admin/ai-jobs/{jobId}", 124L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.error.code").value("LEASE_EXPIRED"))
                .andExpect(jsonPath("$.data.error.retryable").value(true));
    }

    @Test
    void 마지막_작업은_상태_조회와_같은_형식으로_준다() throws Exception {
        when(aiJobService.findLatest(31L, AiJobType.FESTIVAL_LINEUP, null)).thenReturn(Optional.of(new AdminAiJobResponse(
                125L, AiJobType.FESTIVAL_LINEUP, 31L, null, AiJobStatus.DONE,
                mapper.readTree("{\"days\":[]}"), null, 1,
                LocalDateTime.of(2026, 11, 20, 10, 0), LocalDateTime.of(2026, 11, 20, 10, 1))));

        mockMvc.perform(get("/api/admin/ai-jobs/latest")
                        .param("concertId", "31").param("type", "FESTIVAL_LINEUP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value(125))
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.result.days").isArray())
                .andExpect(jsonPath("$.data.finishedAt").exists());
    }

    @Test
    void 찾은_적_없으면_204() throws Exception {
        when(aiJobService.findLatest(31L, AiJobType.CONCERT_ENRICH, null)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/ai-jobs/latest")
                        .param("concertId", "31").param("type", "CONCERT_ENRICH"))
                .andExpect(status().isNoContent());
    }

    @Test
    void 셋리스트의_마지막_작업은_출연_정보로_조회한다() throws Exception {
        when(aiJobService.findLatest(31L, AiJobType.SETLIST_ACTUAL, 101L)).thenReturn(Optional.of(new AdminAiJobResponse(
                126L, AiJobType.SETLIST_ACTUAL, 31L, 101L, AiJobStatus.DONE,
                mapper.readTree("{\"status\":\"PARTIAL\",\"songs\":[]}"), null, 1,
                LocalDateTime.of(2026, 11, 20, 10, 0), LocalDateTime.of(2026, 11, 20, 10, 1))));

        mockMvc.perform(get("/api/admin/ai-jobs/latest")
                        .param("concertId", "31").param("type", "SETLIST_ACTUAL").param("performanceId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.performanceId").value(101))
                .andExpect(jsonPath("$.data.result.songs").isArray());
    }

    @Test
    void 셋리스트_작업은_출연_정보와_함께_만든다() throws Exception {
        when(aiJobService.create(any(), eq(new AdminAiJobCreateRequest(AiJobType.SETLIST_ACTUAL, 31L, 101L))))
                .thenReturn(new AdminAiJobCreateResponse(127L));

        mockMvc.perform(post("/api/admin/ai-jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SETLIST_ACTUAL\",\"concertId\":31,\"performanceId\":101}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value(127));
    }

    @Test
    void 마지막_작업_조회에_공연이나_종류가_없거나_잘못되면_400() throws Exception {
        mockMvc.perform(get("/api/admin/ai-jobs/latest").param("type", "CONCERT_ENRICH"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/ai-jobs/latest").param("concertId", "31"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/ai-jobs/latest").param("concertId", "31").param("type", "UNKNOWN"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(aiJobService);
    }

    @Test
    void 작업을_취소하면_CANCELED_상태를_받고_접근_로그를_남긴다() throws Exception {
        when(aiJobService.cancel(any(), eq(123L))).thenReturn(new AdminAiJobResponse(
                123L, AiJobType.CONCERT_ENRICH, 31L, null, AiJobStatus.CANCELED, null, null, 0,
                LocalDateTime.of(2026, 11, 20, 10, 0), LocalDateTime.of(2026, 11, 20, 10, 5)));

        mockMvc.perform(post("/api/admin/ai-jobs/{jobId}/cancel", 123L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value(123))
                .andExpect(jsonPath("$.data.status").value("CANCELED"));

        verify(accessLog).log(any(), any(), eq("CANCEL_AI_JOB"), eq("CONCERT"), eq(31L), anyString());
    }

    @Test
    void 없는_작업을_취소하면_404() throws Exception {
        when(aiJobService.cancel(any(), eq(999L))).thenThrow(new BusinessException(ErrorCode.AI_JOB_NOT_FOUND));

        mockMvc.perform(post("/api/admin/ai-jobs/{jobId}/cancel", 999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void 이미_끝난_작업을_취소하면_409() throws Exception {
        when(aiJobService.cancel(any(), eq(124L))).thenThrow(new BusinessException(ErrorCode.AI_JOB_ALREADY_FINISHED));

        mockMvc.perform(post("/api/admin/ai-jobs/{jobId}/cancel", 124L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorCode.AI_JOB_ALREADY_FINISHED.getMessage()));
        verifyNoInteractions(accessLog);
    }

    @Test
    void 없는_작업을_조회하면_404() throws Exception {
        when(aiJobService.get(999L)).thenThrow(new BusinessException(ErrorCode.AI_JOB_NOT_FOUND));

        mockMvc.perform(get("/api/admin/ai-jobs/{jobId}", 999L))
                .andExpect(status().isNotFound());
    }
}