package com.loop.loop_backend.Setlist.service;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Setlist.domain.SetlistVote;
import com.loop.loop_backend.Setlist.dto.MySetlistVoteResponse;
import com.loop.loop_backend.Setlist.dto.SetlistCandidatesResponse;
import com.loop.loop_backend.Setlist.dto.SongCandidateResponse;
import com.loop.loop_backend.Setlist.repository.SetlistVoteRepository;
import com.loop.loop_backend.Song.Repository.SongRepository;
import com.loop.loop_backend.Song.domain.Song;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 예상 셋리스트 후보(NO.64)·투표(NO.65).
 * 시각이 필요한 메서드는 now를 받는 오버로드가 있다 - 마감 경계를 테스트에서 고정하려고.
 */
@Service
@RequiredArgsConstructor
public class SetlistVoteService {

    private final SetlistVoteRepository voteRepository;
    private final ConcertRepository concertRepository;
    private final SongRepository songRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public SetlistCandidatesResponse candidates(Long concertId) {
        return candidates(concertId, LocalDateTime.now(SetlistRules.ZONE_KST));
    }

    @Transactional(readOnly = true)
    SetlistCandidatesResponse candidates(Long concertId, LocalDateTime nowKst) {
        Concert concert = findPublishedConcert(concertId);
        if (!SetlistRules.isSetlistConcert(concert)) {
            return new SetlistCandidatesResponse(null, false, List.of());
        }
        // ponytail: 아티스트 곡 전체를 한 번에 - iTunes 불러오기가 수백 곡 수준이라 충분. 수천 곡이 되면 서버 페이징·검색으로
        List<SongCandidateResponse> songs = songRepository
                .findAllByArtistIdOrderBySortOrderAsc(concert.getArtist().getId()).stream()
                .map(SongCandidateResponse::from)
                .toList();
        return new SetlistCandidatesResponse(concert.getExpectedSongCount(),
                SetlistRules.isVotingClosed(concert, nowKst), songs);
    }

    @Transactional(readOnly = true)
    public MySetlistVoteResponse myVote(Long concertId, Long userId) {
        findConcert(concertId);
        return voteRepository.findByConcertIdAndUserId(concertId, userId)
                .map(SetlistVoteService::toResponse)
                .orElseGet(MySetlistVoteResponse::none);
    }

    @Transactional
    public MySetlistVoteResponse saveVote(Long concertId, Long userId, List<Long> songIds) {
        return saveVote(concertId, userId, songIds, LocalDateTime.now(SetlistRules.ZONE_KST));
    }

    /** 공연당 유저 1건. 이미 있으면 곡 선택을 통째로 교체한다. 마감 이후는 409, 곡 수·후보 위반은 400. */
    @Transactional
    MySetlistVoteResponse saveVote(Long concertId, Long userId, List<Long> songIds, LocalDateTime nowKst) {
        Concert concert = findPublishedConcert(concertId);
        if (!SetlistRules.isSetlistConcert(concert)) {
            throw new IllegalArgumentException("예상 셋리스트를 운영하지 않는 공연이다");
        }
        if (SetlistRules.isVotingClosed(concert, nowKst)) {
            throw new BusinessException(ErrorCode.SETLIST_VOTE_CLOSED);
        }
        List<Long> distinct = List.copyOf(new LinkedHashSet<>(songIds));
        if (distinct.isEmpty() || distinct.size() > concert.getExpectedSongCount()) {
            throw new IllegalArgumentException("곡은 1~" + concert.getExpectedSongCount() + "곡: " + distinct.size());
        }
        LinkedHashSet<Song> songs = new LinkedHashSet<>(SetlistRules.resolveSongs(songRepository, concert, distinct));

        SetlistVote vote = voteRepository.findByConcertIdAndUserId(concertId, userId)
                .map(existing -> {
                    existing.replaceSongs(songs);
                    return existing;
                })
                .orElseGet(() -> voteRepository.save(SetlistVote.builder()
                        .concert(concert)
                        .user(userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)))
                        .songs(songs)
                        .build()));
        voteRepository.flush();
        return toResponse(vote);
    }

    private static MySetlistVoteResponse toResponse(SetlistVote vote) {
        List<Long> songIds = vote.getSongs().stream()
                .sorted(Comparator.comparing(Song::getSortOrder).thenComparing(Song::getId))
                .map(Song::getId)
                .toList();
        return new MySetlistVoteResponse(true, songIds, vote.isResultMailConsent());
    }

    private Concert findPublishedConcert(Long concertId) {
        Concert concert = findConcert(concertId);
        SetlistRules.requirePublished(concert);
        return concert;
    }

    private Concert findConcert(Long concertId) {
        return concertRepository.findById(concertId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));
    }
}
