package com.loop.loop_backend.Setlist.service;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Performance.domain.Performance;
import com.loop.loop_backend.Performance.repository.PerformanceRepository;
import com.loop.loop_backend.Song.Repository.SongRepository;
import com.loop.loop_backend.Song.domain.Song;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 셋리스트 서비스들이 같이 쓰는 출연 정보·곡 규칙. */
final class SetlistRules {

    static final ZoneId ZONE_KST = ZoneId.of("Asia/Seoul");

    private SetlistRules() {
    }

    /** 관리자가 예상 곡 수를 넣지 않은 출연 정보(셋리스트 기능 전에 만든 공연 등)에 쓰는 n */
    static final int DEFAULT_EXPECTED_SONG_COUNT = 20;

    /** 경로의 공연 소속 출연 정보. 공연이 없거나 다른 공연의 출연 정보면 404. */
    static Performance findPerformance(PerformanceRepository repository, Long concertId, Long performanceId) {
        return repository.findByIdAndConcertId(performanceId, concertId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND));
    }

    /** 예상 곡 수 n(최대 선택 수·팬 예상 상위 n곡). 미입력이면 기본값. */
    static int expectedSongCount(Performance performance) {
        Integer n = performance.getExpectedSongCount();
        return n != null ? n : DEFAULT_EXPECTED_SONG_COUNT;
    }

    /** 투표 마감 = 공연 시작일 00:00 KST(NO.65). 페스티벌도 DAY와 관계없이 시작일. 시작일 미정이면 마감 없음. */
    static boolean isVotingClosed(Concert concert, LocalDateTime nowKst) {
        return concert.getStartDate() != null && !nowKst.isBefore(concert.getStartDate().atStartOfDay());
    }

    /** 유저 화면용 공연: 비공개(오픈 예정)면 상세·라인업과 같이 403. */
    static void requirePublished(Concert concert) {
        if (!concert.isPublished()) throw new BusinessException(ErrorCode.CONCERT_NOT_OPEN);
    }

    /**
     * 요청 순서·중복 그대로 곡을 꺼낸다. 출연 아티스트의 (삭제되지 않은) 곡만 허용 - 아니면 400.
     * 소프트 삭제된 곡은 Song의 @SQLRestriction 때문에 조회되지 않아 '없는 곡'과 같이 거부된다.
     */
    static List<Song> resolveSongs(SongRepository songRepository, Performance performance, List<Long> songIds) {
        Long artistId = performance.getArtist().getId();
        Map<Long, Song> found = songRepository.findAllById(songIds.stream().distinct().toList()).stream()
                .filter(song -> song.getArtist().getId().equals(artistId))
                .collect(Collectors.toMap(Song::getId, Function.identity()));
        return songIds.stream()
                .map(id -> {
                    Song song = found.get(id);
                    if (song == null) throw new IllegalArgumentException("출연 아티스트의 곡이 아니다: " + id);
                    return song;
                })
                .toList();
    }
}
