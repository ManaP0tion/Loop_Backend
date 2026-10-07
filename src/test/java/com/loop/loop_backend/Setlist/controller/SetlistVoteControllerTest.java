package com.loop.loop_backend.Setlist.controller;

import com.loop.loop_backend.Setlist.dto.MySetlistVoteResponse;
import com.loop.loop_backend.Setlist.dto.SetlistCandidatesResponse;
import com.loop.loop_backend.Setlist.dto.SetlistRankingResponse;
import com.loop.loop_backend.Setlist.dto.SetlistRankingResponse.RankedSong;
import com.loop.loop_backend.Setlist.dto.SetlistResponse;
import com.loop.loop_backend.Setlist.dto.SetlistSongResponse;
import com.loop.loop_backend.Setlist.domain.SetlistType;
import com.loop.loop_backend.Setlist.service.SetlistService;
import com.loop.loop_backend.Setlist.service.SetlistResultService;
import com.loop.loop_backend.Setlist.dto.SetlistResultResponse;
import com.loop.loop_backend.Setlist.domain.HitGrade;
import com.loop.loop_backend.Setlist.dto.SongCandidateResponse;
import com.loop.loop_backend.Setlist.service.SetlistVoteService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 예상 셋리스트 후보·투표 API 요청·응답 요구사항(NO.57·64·65):
// - 후보는 비로그인 열람, n·마감 여부·곡 필드(로마자 포함)를 준다.
// - 투표는 로그인 유저 기준. songIds가 없거나 비었으면 400이고 서비스까지 가지 않는다. 마감 409.
class SetlistVoteControllerTest {

    private static final Long USER_ID = 7L;

    private MockMvc mockMvc;
    private SetlistVoteService service;
    private SetlistService setlistService;
    private SetlistResultService resultService;

    @BeforeEach
    void setUp() {
        service = mock(SetlistVoteService.class);
        setlistService = mock(SetlistService.class);
        resultService = mock(SetlistResultService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SetlistController(service, setlistService, resultService), new SetlistVoteController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 후보는_n_마감_여부_곡_필드를_준다() throws Exception {
        when(service.candidates(1L)).thenReturn(new SetlistCandidatesResponse(20, false,
                List.of(new SongCandidateResponse(12L, "ドライフラワー", "Dry Flower", "드라이플라워", "https://img/a.jpg", 1))));

        mockMvc.perform(get("/api/concerts/{concertId}/setlist/candidates", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxSelect").value(20))
                .andExpect(jsonPath("$.data.votingClosed").value(false))
                .andExpect(jsonPath("$.data.songs[0].songId").value(12))
                .andExpect(jsonPath("$.data.songs[0].titleOriginal").value("ドライフラワー"))
                .andExpect(jsonPath("$.data.songs[0].titleRomanized").value("Dry Flower"))
                .andExpect(jsonPath("$.data.songs[0].titleKo").value("드라이플라워"))
                .andExpect(jsonPath("$.data.songs[0].albumArtUrl").value("https://img/a.jpg"))
                .andExpect(jsonPath("$.data.songs[0].sortOrder").value(1));
    }

    @Test
    void 순위는_로그인_유저를_넘기고_순위_필드를_모두_준다() throws Exception {
        when(service.ranking(1L, USER_ID)).thenReturn(new SetlistRankingResponse(20, 31, true,
                List.of(new RankedSong(1, 12L, "ドライフラワー", "드라이플라워", "https://img/a.jpg", 25, true, true))));

        mockMvc.perform(get("/api/concerts/{concertId}/setlist/ranking", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.highlightCount").value(20))
                .andExpect(jsonPath("$.data.participantCount").value(31))
                .andExpect(jsonPath("$.data.votingClosed").value(true))
                .andExpect(jsonPath("$.data.songs[0].rank").value(1))
                .andExpect(jsonPath("$.data.songs[0].songId").value(12))
                .andExpect(jsonPath("$.data.songs[0].titleOriginal").value("ドライフラワー"))
                .andExpect(jsonPath("$.data.songs[0].titleKo").value("드라이플라워"))
                .andExpect(jsonPath("$.data.songs[0].albumArtUrl").value("https://img/a.jpg"))
                .andExpect(jsonPath("$.data.songs[0].votes").value(25))
                .andExpect(jsonPath("$.data.songs[0].highlighted").value(true))
                .andExpect(jsonPath("$.data.songs[0].mine").value(true));
    }

    @Test
    void 비로그인_순위는_userId_없이_조회한다() throws Exception {
        SecurityContextHolder.clearContext();
        when(service.ranking(1L, null)).thenReturn(new SetlistRankingResponse(20, 0, false, List.of()));

        mockMvc.perform(get("/api/concerts/{concertId}/setlist/ranking", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.songs").isEmpty());
        verify(service).ranking(1L, null);
    }

    @Test
    void 지난_셋리스트는_헤더와_곡을_준다() throws Exception {
        when(setlistService.pastSetlists(1L)).thenReturn(List.of(new SetlistResponse(5L, SetlistType.RECENT, null,
                null, "Zepp", 1, List.of(new SetlistSongResponse(1, 12L, "ドライフラワー", null, null)))));

        mockMvc.perform(get("/api/concerts/{concertId}/setlist/past", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("RECENT"))
                .andExpect(jsonPath("$.data[0].venueName").value("Zepp"))
                .andExpect(jsonPath("$.data[0].songCount").value(1))
                .andExpect(jsonPath("$.data[0].songs[0].titleOriginal").value("ドライフラワー"));
    }

    @Test
    void 결과는_적중률_실제_셋리스트_미출현_곡_필드를_모두_준다() throws Exception {
        when(resultService.result(1L, USER_ID)).thenReturn(new SetlistResultResponse(true, 31,
                new SetlistResultResponse.HitRate(1, 3, 33, HitGrade.LOW), 48,
                new SetlistResultResponse.HitRate(2, 3, 67, HitGrade.MID),
                List.of(new SetlistResultResponse.ResultSong(1, 12L, "ドライフラワー", "드라이플라워", "https://img/a.jpg", true, true, false)),
                List.of(new SetlistResultResponse.MissedSong(7L, "いかないで", null, null, 20, true, false))));

        mockMvc.perform(get("/api/concerts/{concertId}/setlist/result", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(true))
                .andExpect(jsonPath("$.data.participantCount").value(31))
                .andExpect(jsonPath("$.data.overall.hitCount").value(1))
                .andExpect(jsonPath("$.data.overall.totalCount").value(3))
                .andExpect(jsonPath("$.data.overall.percent").value(33))
                .andExpect(jsonPath("$.data.overall.grade").value("LOW"))
                .andExpect(jsonPath("$.data.averagePercent").value(48))
                .andExpect(jsonPath("$.data.mine.grade").value("MID"))
                .andExpect(jsonPath("$.data.songs[0].position").value(1))
                .andExpect(jsonPath("$.data.songs[0].fanPredicted").value(true))
                .andExpect(jsonPath("$.data.songs[0].mine").value(true))
                .andExpect(jsonPath("$.data.songs[0].unexpected").value(false))
                .andExpect(jsonPath("$.data.missedSongs[0].songId").value(7))
                .andExpect(jsonPath("$.data.missedSongs[0].votes").value(20))
                .andExpect(jsonPath("$.data.missedSongs[0].fanPredicted").value(true));
    }

    @Test
    void 수신_동의는_로그인_유저_기준이고_투표가_없으면_404() throws Exception {
        when(service.agreeResultMail(1L, USER_ID)).thenReturn(new MySetlistVoteResponse(true, List.of(12L), true));
        mockMvc.perform(put("/api/users/me/setlist-votes/{concertId}/result-mail-consent", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultMailConsent").value(true));

        when(service.agreeResultMail(2L, USER_ID)).thenThrow(new BusinessException(ErrorCode.SETLIST_VOTE_NOT_FOUND));
        mockMvc.perform(put("/api/users/me/setlist-votes/{concertId}/result-mail-consent", 2L))
                .andExpect(status().isNotFound());
    }

    @Test
    void 비공개_공연_후보는_403() throws Exception {
        when(service.candidates(1L)).thenThrow(new BusinessException(ErrorCode.CONCERT_NOT_OPEN));

        mockMvc.perform(get("/api/concerts/{concertId}/setlist/candidates", 1L))
                .andExpect(status().isForbidden());
    }

    @Test
    void 내_투표는_로그인_유저_기준으로_조회한다() throws Exception {
        when(service.myVote(1L, USER_ID)).thenReturn(new MySetlistVoteResponse(true, List.of(12L, 7L), true));

        mockMvc.perform(get("/api/users/me/setlist-votes/{concertId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.voted").value(true))
                .andExpect(jsonPath("$.data.songIds[1]").value(7))
                .andExpect(jsonPath("$.data.resultMailConsent").value(true));
    }

    @Test
    void 투표는_로그인_유저와_곡_목록을_넘긴다() throws Exception {
        when(service.saveVote(1L, USER_ID, List.of(12L, 7L))).thenReturn(new MySetlistVoteResponse(true, List.of(7L, 12L), false));

        mockMvc.perform(put("/api/users/me/setlist-votes/{concertId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"songIds\":[12,7]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.songIds[0]").value(7));
    }

    @Test
    void songIds가_없거나_비었으면_400이고_서비스는_호출되지_않는다() throws Exception {
        mockMvc.perform(put("/api/users/me/setlist-votes/{concertId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/users/me/setlist-votes/{concertId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"songIds\":[]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void 마감_이후_투표는_409_곡_수_위반은_400() throws Exception {
        when(service.saveVote(eq(1L), eq(USER_ID), anyList())).thenThrow(new BusinessException(ErrorCode.SETLIST_VOTE_CLOSED));
        mockMvc.perform(put("/api/users/me/setlist-votes/{concertId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"songIds\":[1]}"))
                .andExpect(status().isConflict());

        reset(service);
        when(service.saveVote(any(), any(), anyList())).thenThrow(new IllegalArgumentException("곡은 1~2곡"));
        mockMvc.perform(put("/api/users/me/setlist-votes/{concertId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"songIds\":[1,2,3]}"))
                .andExpect(status().isBadRequest());
    }
}
