package com.loop.loop_backend.FavoriteArtist.controller;

import com.loop.loop_backend.FavoriteArtist.dto.FavoriteArtistResponseDto;
import com.loop.loop_backend.FavoriteArtist.service.FavoriteArtistService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FavoriteArtistControllerTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    private MockMvc mockMvc;
    private FavoriteArtistService favoriteArtistService;

    @BeforeEach
    void setUp() {
        favoriteArtistService = mock(FavoriteArtistService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new FavoriteArtistController(favoriteArtistService))
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

    // ── GET /{userId}/artists ─────────────────────────────────────────────

    @Test
    void 사용자_PK로_관심_아티스트_목록을_조회하면_200과_목록을_반환한다() throws Exception {
        when(favoriteArtistService.getFavoriteArtists(OTHER_USER_ID)).thenReturn(List.of(
                FavoriteArtistResponseDto.builder().id(1L).artistId(10L).artistName("아이유").build()
        ));

        mockMvc.perform(get("/api/users/{userId}/artists", OTHER_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].artistName").value("아이유"));
    }

    @Test
    void 존재하지_않는_사용자_PK로_조회하면_404를_반환한다() throws Exception {
        when(favoriteArtistService.getFavoriteArtists(OTHER_USER_ID))
                .thenThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/api/users/{userId}/artists", OTHER_USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ── POST /me/artists ─────────────────────────────────────────────────

    @Test
    void 관심_아티스트_추가시_201과_생성된_정보를_반환한다() throws Exception {
        when(favoriteArtistService.addFavoriteArtist(eq(USER_ID), eq(10L)))
                .thenReturn(FavoriteArtistResponseDto.builder().id(5L).artistId(10L).artistName("아이유").build());

        mockMvc.perform(post("/api/users/me/artists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"artistId\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.artistName").value("아이유"));
    }

    @Test
    void 아티스트ID가_없으면_400을_반환하고_서비스는_호출되지_않는다() throws Exception {
        mockMvc.perform(post("/api/users/me/artists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(favoriteArtistService);
    }

    @Test
    void 존재하지_않는_아티스트_추가시_404를_반환한다() throws Exception {
        when(favoriteArtistService.addFavoriteArtist(eq(USER_ID), eq(999L)))
                .thenThrow(new BusinessException(ErrorCode.ARTIST_NOT_FOUND));

        mockMvc.perform(post("/api/users/me/artists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"artistId\":999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void 이미_등록된_아티스트_추가시_409를_반환한다() throws Exception {
        when(favoriteArtistService.addFavoriteArtist(eq(USER_ID), eq(10L)))
                .thenThrow(new BusinessException(ErrorCode.DUPLICATE_FAVORITE_ARTIST));

        mockMvc.perform(post("/api/users/me/artists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"artistId\":10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void 개수_초과시_409를_반환한다() throws Exception {
        when(favoriteArtistService.addFavoriteArtist(eq(USER_ID), eq(10L)))
                .thenThrow(new BusinessException(ErrorCode.LIMIT_FAVORITE_ARTIST));

        mockMvc.perform(post("/api/users/me/artists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"artistId\":10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    // ── DELETE /me/artists/{id} ──────────────────────────────────────────

    @Test
    void 관심_아티스트_삭제시_200과_공통_응답을_반환하고_로그인한_유저_기준으로_삭제를_요청한다() throws Exception {
        mockMvc.perform(delete("/api/users/me/artists/{id}", 5L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(favoriteArtistService).deleteFavoriteArtist(USER_ID, 5L);
    }

    @Test
    void 존재하지_않는_관심_아티스트_삭제시_404를_반환한다() throws Exception {
        doThrow(new BusinessException(ErrorCode.FAVORITE_ARTIST_NOT_FOUND))
                .when(favoriteArtistService).deleteFavoriteArtist(USER_ID, 999L);

        mockMvc.perform(delete("/api/users/me/artists/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }
}