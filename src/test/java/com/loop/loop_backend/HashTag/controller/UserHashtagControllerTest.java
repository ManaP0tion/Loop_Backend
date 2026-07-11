package com.loop.loop_backend.HashTag.controller;

import com.loop.loop_backend.HashTag.dto.HashtagResponseDto;
import com.loop.loop_backend.HashTag.service.UserHashtagService;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import com.loop.loop_backend.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserHashtagControllerTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    private MockMvc mockMvc;
    private UserHashtagService userHashtagService;

    @BeforeEach
    void setUp() {
        userHashtagService = mock(UserHashtagService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new UserHashtagController(userHashtagService))
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

    // ── GET /{userId}/hashtags ───────────────────────────────────────────────

    @Test
    void 사용자_PK로_해시태그_목록을_조회하면_200과_목록을_반환한다() throws Exception {
        when(userHashtagService.getHashtags(OTHER_USER_ID)).thenReturn(List.of(
                HashtagResponseDto.builder().id(3L).tag("재즈").build()
        ));

        mockMvc.perform(get("/api/users/{userId}/hashtags", OTHER_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].tag").value("재즈"));
    }

    @Test
    void 존재하지_않는_사용자_PK로_조회하면_404를_반환한다() throws Exception {
        when(userHashtagService.getHashtags(OTHER_USER_ID))
                .thenThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/api/users/{userId}/hashtags", OTHER_USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ── POST /me/hashtags ────────────────────────────────────────────────────

    @Test
    void 해시태그_추가시_201과_생성된_해시태그를_반환한다() throws Exception {
        when(userHashtagService.addHashtag(eq(USER_ID), eq("굿즈")))
                .thenReturn(HashtagResponseDto.builder().id(10L).tag("굿즈").build());

        mockMvc.perform(post("/api/users/me/hashtags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tag\":\"굿즈\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.tag").value("굿즈"));
    }

    @Test
    void 태그_앞뒤_공백은_트리밍되어_검증과_저장이_통과된다() throws Exception {
        when(userHashtagService.addHashtag(eq(USER_ID), eq("굿즈")))
                .thenReturn(HashtagResponseDto.builder().id(11L).tag("굿즈").build());

        mockMvc.perform(post("/api/users/me/hashtags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tag\":\"  굿즈  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tag").value("굿즈"));

        verify(userHashtagService).addHashtag(USER_ID, "굿즈");
    }

    @Test
    void 태그가_비어있으면_400을_반환하고_서비스는_호출되지_않는다() throws Exception {
        mockMvc.perform(post("/api/users/me/hashtags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tag\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userHashtagService);
    }

    @Test
    void 태그가_5자를_초과하면_400을_반환하고_서비스는_호출되지_않는다() throws Exception {
        mockMvc.perform(post("/api/users/me/hashtags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tag\":\"123456\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userHashtagService);
    }

    @Test
    void 태그에_특수문자가_있으면_400을_반환하고_서비스는_호출되지_않는다() throws Exception {
        mockMvc.perform(post("/api/users/me/hashtags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tag\":\"a!b\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userHashtagService);
    }

    @Test
    void 이미_등록된_해시태그를_추가하면_409를_반환한다() throws Exception {
        when(userHashtagService.addHashtag(eq(USER_ID), anyString()))
                .thenThrow(new BusinessException(ErrorCode.DUPLICATE_HASHTAG));

        mockMvc.perform(post("/api/users/me/hashtags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tag\":\"굿즈\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void 해시태그_개수가_초과되면_409를_반환한다() throws Exception {
        when(userHashtagService.addHashtag(eq(USER_ID), anyString()))
                .thenThrow(new BusinessException(ErrorCode.LIMIT_HASHTAG));

        mockMvc.perform(post("/api/users/me/hashtags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tag\":\"굿즈\"}"))
                .andExpect(status().isConflict());
    }

    // ── DELETE /me/hashtags/{hashtagId} ──────────────────────────────────────

    @Test
    void 해시태그_삭제시_200과_공통_응답을_반환하고_로그인한_유저_기준으로_삭제를_요청한다() throws Exception {
        mockMvc.perform(delete("/api/users/me/hashtags/{id}", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userHashtagService).deleteHashtag(USER_ID, 10L);
    }

    @Test
    void 존재하지_않는_해시태그_삭제시_404를_반환한다() throws Exception {
        doThrow(new BusinessException(ErrorCode.HASHTAG_NOT_FOUND))
                .when(userHashtagService).deleteHashtag(USER_ID, 999L);

        mockMvc.perform(delete("/api/users/me/hashtags/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }
}