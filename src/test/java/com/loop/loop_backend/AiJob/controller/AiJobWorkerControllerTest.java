package com.loop.loop_backend.AiJob.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.loop_backend.AiJob.domain.AiJobType;
import com.loop.loop_backend.AiJob.dto.AiJobClaimResponse;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 워커 API의 HTTP 약속(연동 약속 3절).
 * - claim: 작업 없음 204(본문 없음), 있음 200 + {jobId, type, payload(JSON 객체), attempt}
 * - result: 저장 200, 없는 jobId 404, 받지 않음 409, 형식 오류 400
 */
@ExtendWith(MockitoExtension.class)
class AiJobWorkerControllerTest {

    @Mock AiJobService aiJobService;
    @InjectMocks AiJobWorkerController controller;

    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static final String CLAIM_BODY =
            "{\"workerId\":\"pm-macbook\",\"types\":[\"CONCERT_ENRICH\",\"SETLIST_ACTUAL\",\"FESTIVAL_LINEUP\"]}";

    @Test
    void 줄_작업이_없으면_본문_없이_204() throws Exception {
        when(aiJobService.claim(eq("pm-macbook"), any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/internal/ai-jobs/claim").contentType(MediaType.APPLICATION_JSON).content(CLAIM_BODY))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void 줄_작업이_있으면_200과_함께_작업ID_종류_payload_객체_시도번호를_준다() throws Exception {
        when(aiJobService.claim("pm-macbook", List.of("CONCERT_ENRICH", "SETLIST_ACTUAL", "FESTIVAL_LINEUP")))
                .thenReturn(Optional.of(new AiJobClaimResponse(123L, AiJobType.FESTIVAL_LINEUP,
                        mapper.readTree("{\"concertId\":200,\"title\":\"WONDERLIVET 2026\",\"startDate\":\"2026-11-20\"}"), 1)));

        mockMvc.perform(post("/api/internal/ai-jobs/claim").contentType(MediaType.APPLICATION_JSON).content(CLAIM_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").value(123))
                .andExpect(jsonPath("$.data.type").value("FESTIVAL_LINEUP"))
                .andExpect(jsonPath("$.data.payload.concertId").value(200))
                .andExpect(jsonPath("$.data.payload.startDate").value("2026-11-20"))
                .andExpect(jsonPath("$.data.attempt").value(1));
    }

    @Test
    void 워커_식별자가_없으면_400이고_작업을_배정하지_않는다() throws Exception {
        mockMvc.perform(post("/api/internal/ai-jobs/claim").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"types\":[\"CONCERT_ENRICH\"]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(aiJobService);
    }

    @Test
    void 결과를_저장하면_200() throws Exception {
        mockMvc.perform(post("/api/internal/ai-jobs/{jobId}/result", 123L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workerId\":\"pm-macbook\",\"attempt\":1,\"status\":\"DONE\",\"result\":{\"status\":\"NOT_FOUND\"}}"))
                .andExpect(status().isOk());

        verify(aiJobService).report(eq(123L), any());
    }

    @Test
    void 없는_작업에_보고하면_404() throws Exception {
        doThrow(new BusinessException(ErrorCode.AI_JOB_NOT_FOUND)).when(aiJobService).report(eq(999L), any());

        mockMvc.perform(post("/api/internal/ai-jobs/{jobId}/result", 999L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workerId\":\"pm-macbook\",\"attempt\":1,\"status\":\"DONE\",\"result\":{}}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 받을_수_없는_보고는_409() throws Exception {
        doThrow(new BusinessException(ErrorCode.AI_JOB_NOT_ACCEPTED)).when(aiJobService).report(eq(123L), any());

        mockMvc.perform(post("/api/internal/ai-jobs/{jobId}/result", 123L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workerId\":\"pm-macbook\",\"attempt\":1,\"status\":\"DONE\",\"result\":{}}"))
                .andExpect(status().isConflict());
    }

    @Test
    void 결과_없는_DONE_보고는_400이고_저장하지_않는다() throws Exception {
        mockMvc.perform(post("/api/internal/ai-jobs/{jobId}/result", 123L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workerId\":\"pm-macbook\",\"attempt\":1,\"status\":\"DONE\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(aiJobService);
    }

    @Test
    void DONE_FAILED_외의_상태_보고는_400이고_저장하지_않는다() throws Exception {
        mockMvc.perform(post("/api/internal/ai-jobs/{jobId}/result", 123L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workerId\":\"pm-macbook\",\"attempt\":1,\"status\":\"RUNNING\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(aiJobService);
    }
}