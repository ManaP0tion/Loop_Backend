package com.loop.loop_backend.Setlist.service;

import com.loop.loop_backend.Performance.domain.Performance;
import com.loop.loop_backend.Performance.repository.PerformanceRepository;
import com.loop.loop_backend.Setlist.domain.SetlistVote;
import com.loop.loop_backend.Setlist.dto.MySetlistVoteResponse;
import com.loop.loop_backend.Setlist.dto.SetlistCandidatesResponse;
import com.loop.loop_backend.Setlist.dto.SetlistRankingResponse;
import com.loop.loop_backend.Setlist.dto.SetlistRankingResponse.RankedSong;
import com.loop.loop_backend.Setlist.dto.SongCandidateResponse;
import com.loop.loop_backend.Setlist.repository.SetlistVoteRepository;
import com.loop.loop_backend.Setlist.repository.SongVoteCount;
import com.loop.loop_backend.Song.Repository.SongRepository;
import com.loop.loop_backend.Song.domain.Song;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 예상 셋리스트 후보(NO.64)·투표(NO.65). 출연 정보(단독 공연·페스티벌 헤드라이너) 단위다.
 * 시각이 필요한 메서드는 now를 받는 오버로드가 있다 - 마감 경계를 테스트에서 고정하려고.
 */
@Service
@RequiredArgsConstructor
public class SetlistVoteService {

    private final SetlistVoteRepository voteRepository;
    private final PerformanceRepository performanceRepository;
    private final SongRepository songRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public SetlistCandidatesResponse candidates(Long concertId, Long performanceId) {
        return candidates(concertId, performanceId, LocalDateTime.now(SetlistRules.ZONE_KST));
    }

    @Transactional(readOnly = true)
    SetlistCandidatesResponse candidates(Long concertId, Long performanceId, LocalDateTime nowKst) {
        Performance performance = findPublishedPerformance(concertId, performanceId);
        if (!performance.isSetlistTarget()) {
            return new SetlistCandidatesResponse(null, false, List.of());
        }
        // ponytail: 아티스트 곡 전체를 한 번에 - iTunes 불러오기가 수백 곡 수준이라 충분. 수천 곡이 되면 서버 페이징·검색으로
        List<SongCandidateResponse> songs = songRepository
                .findAllByArtistIdOrderBySortOrderAsc(performance.getArtist().getId()).stream()
                .map(SongCandidateResponse::from)
                .toList();
        return new SetlistCandidatesResponse(SetlistRules.expectedSongCount(performance),
                SetlistRules.isVotingClosed(performance.getConcert(), nowKst), songs);
    }

    @Transactional(readOnly = true)
    public SetlistRankingResponse ranking(Long concertId, Long performanceId, Long userId) {
        return ranking(concertId, performanceId, userId, LocalDateTime.now(SetlistRules.ZONE_KST));
    }

    /** 공연 상세 화면용 순위 미리보기(상위 5곡). 전체 순위는 ranking. */
    @Transactional(readOnly = true)
    public SetlistRankingResponse rankingPreview(Long concertId, Long performanceId, Long userId) {
        return ranking(concertId, performanceId, userId).top(5);
    }

    /** 순위(NO.66). userId가 있으면(로그인) 내 선택 곡에 mine 표시. */
    @Transactional(readOnly = true)
    SetlistRankingResponse ranking(Long concertId, Long performanceId, Long userId, LocalDateTime nowKst) {
        Performance performance = findPublishedPerformance(concertId, performanceId);
        if (!performance.isSetlistTarget()) {
            return SetlistRankingResponse.none();
        }
        int n = SetlistRules.expectedSongCount(performance);
        Set<Long> mine = userId == null ? Set.of() : voteRepository.findByPerformanceIdAndUserId(performanceId, userId)
                .map(v -> v.getSongs().stream().map(Song::getId).collect(Collectors.toSet()))
                .orElse(Set.of());
        List<SongVoteCount> counts = voteRepository.countVotesBySong(performanceId);
        Map<Long, Song> songs = songRepository.findAllById(counts.stream().map(SongVoteCount::songId).toList()).stream()
                .collect(Collectors.toMap(Song::getId, Function.identity()));

        List<RankedSong> ranked = new ArrayList<>();
        for (SongVoteCount count : counts) {
            int rank = ranked.size() + 1;
            ranked.add(RankedSong.of(rank, songs.get(count.songId()), count.votes(), rank <= n,
                    mine.contains(count.songId())));
        }
        return new SetlistRankingResponse(n, voteRepository.countByPerformanceId(performanceId),
                SetlistRules.isVotingClosed(performance.getConcert(), nowKst), ranked);
    }

    @Transactional(readOnly = true)
    public MySetlistVoteResponse myVote(Long concertId, Long performanceId, Long userId) {
        SetlistRules.findPerformance(performanceRepository, concertId, performanceId);
        return voteRepository.findByPerformanceIdAndUserId(performanceId, userId)
                .map(SetlistVoteService::toResponse)
                .orElseGet(MySetlistVoteResponse::none);
    }

    @Transactional
    public MySetlistVoteResponse saveVote(Long concertId, Long performanceId, Long userId, List<Long> songIds) {
        return saveVote(concertId, performanceId, userId, songIds, LocalDateTime.now(SetlistRules.ZONE_KST));
    }

    /** 출연 정보당 유저 1건. 이미 있으면 곡 선택을 통째로 교체한다. 마감 이후는 409, 곡 수·후보 위반은 400. */
    @Transactional
    MySetlistVoteResponse saveVote(Long concertId, Long performanceId, Long userId, List<Long> songIds,
                                   LocalDateTime nowKst) {
        Performance performance = findPublishedPerformance(concertId, performanceId);
        if (!performance.isSetlistTarget()) {
            throw new IllegalArgumentException("예상 셋리스트를 운영하지 않는 출연 정보다(페스티벌은 헤드라이너만)");
        }
        if (SetlistRules.isVotingClosed(performance.getConcert(), nowKst)) {
            throw new BusinessException(ErrorCode.SETLIST_VOTE_CLOSED);
        }
        int n = SetlistRules.expectedSongCount(performance);
        List<Long> distinct = List.copyOf(new LinkedHashSet<>(songIds));
        if (distinct.isEmpty() || distinct.size() > n) {
            throw new IllegalArgumentException("곡은 1~" + n + "곡: " + distinct.size());
        }
        LinkedHashSet<Song> songs = new LinkedHashSet<>(SetlistRules.resolveSongs(songRepository, performance, distinct));

        SetlistVote vote = voteRepository.findByPerformanceIdAndUserId(performanceId, userId)
                .map(existing -> {
                    existing.replaceSongs(songs);
                    return existing;
                })
                .orElseGet(() -> voteRepository.save(SetlistVote.builder()
                        .performance(performance)
                        .user(userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)))
                        .songs(songs)
                        .build()));
        voteRepository.flush();
        return toResponse(vote);
    }

    /** 결과 메일 수신 동의(MD03 1단계). 투표한 유저만 - 아니면 404. 이미 동의했어도 200. */
    @Transactional
    public MySetlistVoteResponse agreeResultMail(Long concertId, Long performanceId, Long userId) {
        SetlistRules.findPerformance(performanceRepository, concertId, performanceId);
        SetlistVote vote = voteRepository.findByPerformanceIdAndUserId(performanceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETLIST_VOTE_NOT_FOUND));
        vote.agreeResultMail();
        return toResponse(vote);
    }

    private static MySetlistVoteResponse toResponse(SetlistVote vote) {
        List<Long> songIds = vote.getSongs().stream()
                .sorted(Comparator.comparing(Song::getSortOrder).thenComparing(Song::getId))
                .map(Song::getId)
                .toList();
        return new MySetlistVoteResponse(true, songIds, vote.isResultMailConsent());
    }

    private Performance findPublishedPerformance(Long concertId, Long performanceId) {
        Performance performance = SetlistRules.findPerformance(performanceRepository, concertId, performanceId);
        SetlistRules.requirePublished(performance.getConcert());
        return performance;
    }
}
