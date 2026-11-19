package com.loop.loop_backend.AiJob.security;

import com.loop.loop_backend.AiJob.controller.AiJobWorkerController;
import com.loop.loop_backend.AiJob.service.AiJobService;
import com.loop.loop_backend.Config.SecurityConfig;
import com.loop.loop_backend.common.jwt.TokenAuthenticator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 SecurityConfig로 워커 API 인증을 확인한다(연동 약속 1절).
 * - 워커 → Backend 모든 요청에 X-API-Key. 틀리거나 없으면 401.
 * - 이 API는 어드민·사용자 인증(JWT)을 타지 않는다 - JWT로는 들어올 수 없다.
 * - API 키는 워커 API에서만 통한다 - 다른 API(어드민 등)의 인증을 대신하지 않는다.
 */
@WebMvcTest(controllers = AiJobWorkerController.class, properties = {
        "ai-worker.api-key=s3cret-worker-key",
        "cors.allowed-origins=http://localhost:3000",
        "swagger.username=swagger",
        "swagger.password=swagger"
})
@Import(SecurityConfig.class)
class AiWorkerSecurityTest {

    private static final String KEY = "s3cret-worker-key";
    private static final String CLAIM_BODY = "{\"workerId\":\"pm-macbook\",\"types\":[\"CONCERT_ENRICH\"]}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AiJobService aiJobService;
    @MockitoBean private TokenAuthenticator tokenAuthenticator;

    @Test
    void 맞는_키면_워커_API에_닿는다() throws Exception {
        when(aiJobService.claim(anyString(), anyList())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/internal/ai-jobs/claim").header("X-API-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(CLAIM_BODY))
                .andExpect(status().isNoContent());

        verify(aiJobService).claim(anyString(), anyList());
    }

    @Test
    void 키가_없으면_401이고_작업을_건드리지_않는다() throws Exception {
        mockMvc.perform(post("/api/internal/ai-jobs/claim")
                        .contentType(MediaType.APPLICATION_JSON).content(CLAIM_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(aiJobService);
    }

    @Test
    void 키가_틀리면_401이고_결과를_저장하지_않는다() throws Exception {
        mockMvc.perform(post("/api/internal/ai-jobs/{jobId}/result", 1L).header("X-API-Key", "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workerId\":\"pm-macbook\",\"attempt\":1,\"status\":\"DONE\",\"result\":{}}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(aiJobService);
    }

    @Test
    void JWT로는_워커_API에_들어올_수_없다() throws Exception {
        mockMvc.perform(post("/api/internal/ai-jobs/claim").header("Authorization", "Bearer admin-access-token")
                        .contentType(MediaType.APPLICATION_JSON).content(CLAIM_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(aiJobService);
        verify(tokenAuthenticator, never()).authenticate(any());
    }

    @Test
    void API_키로는_다른_API의_인증을_대신할_수_없다() throws Exception {
        when(tokenAuthenticator.authenticate(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/ai-jobs/{jobId}", 1L).header("X-API-Key", KEY))
                .andExpect(status().isUnauthorized());
    }
}