package com.loop.loop_backend.Setlist.service;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Setlist.domain.Setlist;
import com.loop.loop_backend.Setlist.domain.SetlistType;
import com.loop.loop_backend.Setlist.dto.SetlistResultResponse;
import com.loop.loop_backend.Setlist.dto.SetlistResultResponse.HitRate;
import com.loop.loop_backend.Setlist.dto.SetlistResultResponse.MissedSong;
import com.loop.loop_backend.Setlist.dto.SetlistResultResponse.ResultSong;
import com.loop.loop_backend.Setlist.repository.SetlistRepository;
import com.loop.loop_backend.Setlist.repository.SetlistVoteRepository;
import com.loop.loop_backend.Setlist.repository.SongVoteCount;
import com.loop.loop_backend.Setlist.repository.VoteSongRow;
import com.loop.loop_backend.Song.Repository.SongRepository;
import com.loop.loop_backend.Song.domain.Song;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 적중률 산출·공연 후 결과(NO.60~62, 68).
 * 결과는 저장하지 않고 조회할 때 계산한다 - 실제 셋리스트를 고치면 다음 조회부터 바로 재산출된 값이 나온다.
 * ponytail: 공연 하나의 투표 전체를 읽어 계산. 투표가 수만 건이 되면 실제 셋리스트 저장 시점에 결과를 스냅샷 테이블로.
 */
@Service
@RequiredArgsConstructor
public class SetlistResultService {

    private final SetlistRepository setlistRepository;
    private final SetlistVoteRepository voteRepository;
    private final ConcertRepository concertRepository;
    private final SongRepository songRepository;

    @Transactional(readOnly = true)
    public SetlistResultResponse result(Long concertId, Long userId) {
        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));
        SetlistRules.requirePublished(concert);
        long participants = voteRepository.countByConcertId(concertId);
        Setlist actualSetlist = SetlistRules.isSetlistConcert(concert)
                ? setlistRepository.findByConcertIdAndType(concertId, SetlistType.ACTUAL).orElse(null)
                : null;
        // 소프트 삭제된 곡은 null 자리로 온다
        List<Song> actualSongs = actualSetlist == null ? List.of()
                : actualSetlist.getSongs().stream().filter(Objects::nonNull).toList();
        if (actualSongs.isEmpty()) {
            return SetlistResultResponse.waiting(participants);
        }

        // 분모 = 실제 곡 수(중복 연주 1회)
        Set<Long> actual = actualSongs.stream().map(Song::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        int total = actual.size();

        List<SongVoteCount> counts = voteRepository.countVotesBySong(concertId);
        Map<Long, Long> votesBySong = counts.stream()
                .collect(Collectors.toMap(SongVoteCount::songId, SongVoteCount::votes));
        Set<Long> fanTop = counts.stream().limit(concert.getExpectedSongCount())
                .map(SongVoteCount::songId).collect(Collectors.toSet());

        Map<Long, Set<Long>> songsByVote = voteRepository.findVoteSongs(concertId).stream()
                .collect(Collectors.groupingBy(VoteSongRow::voteId,
                        Collectors.mapping(VoteSongRow::songId, Collectors.toSet())));
        Set<Long> mine = userId == null ? null : voteRepository.findByConcertIdAndUserId(concertId, userId)
                .map(v -> songsByVote.getOrDefault(v.getId(), Set.of()))
                .orElse(null);

        HitRate overall = HitRate.of(countHits(fanTop, actual), total);
        Integer average = songsByVote.isEmpty() ? null : (int) Math.round(songsByVote.values().stream()
                .mapToDouble(picks -> countHits(picks, actual) * 100.0 / total)
                .average().orElseThrow());
        HitRate myRate = mine == null ? null : HitRate.of(countHits(mine, actual), total);
        // 상위 n% = (나보다 많이 맞힌 투표자 수 + 1) ÷ 참여자 수, 올림. 동점은 같은 순위
        Integer myTopPercent = myRate == null ? null : (int) Math.ceil(100.0 * (1 + songsByVote.values().stream()
                .filter(picks -> countHits(picks, actual) > myRate.hitCount()).count()) / participants);
        Set<Long> myPicks = mine == null ? Set.of() : mine;

        List<ResultSong> songs = new ArrayList<>();
        for (Song song : actualSongs) {
            songs.add(ResultSong.of(songs.size() + 1, song, fanTop.contains(song.getId()), myPicks.contains(song.getId()),
                    !votesBySong.containsKey(song.getId())));
        }

        List<SongVoteCount> missedCounts = counts.stream().filter(c -> !actual.contains(c.songId())).toList();
        Map<Long, Song> missedSongs = songRepository.findAllById(missedCounts.stream().map(SongVoteCount::songId).toList())
                .stream().collect(Collectors.toMap(Song::getId, Function.identity()));
        List<MissedSong> missed = missedCounts.stream()
                .map(c -> MissedSong.of(missedSongs.get(c.songId()), c.votes(), fanTop.contains(c.songId()),
                        myPicks.contains(c.songId())))
                .toList();

        return new SetlistResultResponse(true, participants, overall, average, myRate, myTopPercent, songs, missed);
    }

    private static int countHits(Set<Long> picks, Set<Long> actual) {
        return (int) picks.stream().filter(actual::contains).count();
    }
}
