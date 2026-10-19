package com.loop.loop_backend.Setlist.service;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Performance.domain.Performance;
import com.loop.loop_backend.Performance.repository.PerformanceRepository;
import com.loop.loop_backend.Setlist.domain.Setlist;
import com.loop.loop_backend.Setlist.domain.SetlistType;
import com.loop.loop_backend.Setlist.dto.PreviousActualSetlistResponse;
import com.loop.loop_backend.Setlist.dto.SetlistResponse;
import com.loop.loop_backend.Setlist.dto.SetlistSaveRequest;
import com.loop.loop_backend.Setlist.event.SetlistResultSavedEvent;
import com.loop.loop_backend.Setlist.repository.SetlistRepository;
import com.loop.loop_backend.Setlist.repository.SetlistSong;
import com.loop.loop_backend.Song.Repository.SongRepository;
import com.loop.loop_backend.Song.domain.Song;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 셋리스트 관리(AD-07). 지난 셋리스트와 실제 셋리스트를 같은 방식(후보 곡 체크 + 순서)으로 저장한다.
 * 출연 정보(단독 공연·페스티벌 헤드라이너) 단위다. 대상 여부·헤더 필수값·곡 1곡 이상은 Setlist 엔티티가 검사한다(위반 시 400).
 */
@Service
@RequiredArgsConstructor
public class SetlistService {

    /** 이전 실제 셋리스트 불러오기 최대 건수 */
    static final int PREVIOUS_ACTUAL_LIMIT = 10;

    private final SetlistRepository setlistRepository;
    private final PerformanceRepository performanceRepository;
    private final SongRepository songRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 관리자 목록: 최근 공연 → 지난 내한 → 실제 순. */
    @Transactional(readOnly = true)
    public List<SetlistResponse> list(Long concertId, Long performanceId) {
        SetlistRules.findPerformance(performanceRepository, concertId, performanceId);
        return currentList(performanceId);
    }

    /** 유저용 지난 셋리스트(NO.67): 최근 공연 → 지난 내한, 최대 2건. 비공개 공연 403. */
    @Transactional(readOnly = true)
    public List<SetlistResponse> pastSetlists(Long concertId, Long performanceId) {
        Performance performance = SetlistRules.findPerformance(performanceRepository, concertId, performanceId);
        SetlistRules.requirePublished(performance.getConcert());
        return currentList(performanceId).stream().filter(s -> s.type().isPast()).toList();
    }

    /** 생성 또는 통째 교체. 응답은 그 출연 정보의 셋리스트 전체. */
    @Transactional
    public List<SetlistResponse> save(Long concertId, Long performanceId, SetlistType type, SetlistSaveRequest req) {
        Performance performance = SetlistRules.findPerformance(performanceRepository, concertId, performanceId);
        List<Song> songs = SetlistRules.resolveSongs(songRepository, performance, req.songIds());
        Setlist setlist = setlistRepository.findByPerformanceIdAndType(performanceId, type)
                .map(existing -> {
                    existing.changeHeader(req.tourName(), req.performedOn(), req.venueName());
                    existing.replaceSongs(songs);
                    return existing;
                })
                .orElseGet(() -> setlistRepository.save(Setlist.builder()
                        .performance(performance).type(type)
                        .tourName(req.tourName()).performedOn(req.performedOn()).venueName(req.venueName())
                        .songs(songs)
                        .build()));
        // 결과 메일은 실제 셋리스트 최초 저장 때만(NO.69). 수정은 적중률만 바뀐다(조회 시 계산)
        if (setlist.markResultMailSent(LocalDateTime.now(SetlistRules.ZONE_KST))) {
            Concert concert = performance.getConcert();
            eventPublisher.publishEvent(new SetlistResultSavedEvent(concertId, performanceId, concert.getTitle()));
        }
        setlistRepository.flush();
        return currentList(performanceId);
    }

    /** 지난 셋리스트만 지울 수 있다. 실제 셋리스트는 결과 메일 기록(최초 1회)이 함께 지워지므로 수정(PUT)만 허용 - 400. */
    @Transactional
    public List<SetlistResponse> delete(Long concertId, Long performanceId, SetlistType type) {
        SetlistRules.findPerformance(performanceRepository, concertId, performanceId);
        if (!type.isPast()) {
            throw new IllegalArgumentException("실제 셋리스트는 삭제할 수 없다 - 수정으로 교체한다");
        }
        Setlist setlist = setlistRepository.findByPerformanceIdAndType(performanceId, type)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETLIST_NOT_FOUND));
        setlistRepository.delete(setlist);
        setlistRepository.flush();
        return currentList(performanceId);
    }

    /**
     * 이전 실제 셋리스트 불러오기(F-1): 같은 아티스트의 다른 공연(단독·페스티벌 헤드라이너)에 저장된 실제 셋리스트,
     * 공연일 최신순 최대 10건. 관리자가 골라 지난 셋리스트 입력 폼을 채우고 기존 save로 저장한다(복사).
     */
    @Transactional(readOnly = true)
    public List<PreviousActualSetlistResponse> previousActuals(Long concertId, Long performanceId) {
        Performance performance = SetlistRules.findPerformance(performanceRepository, concertId, performanceId);
        List<Setlist> setlists = setlistRepository.findActualsByArtist(performance.getArtist().getId(), performanceId,
                PageRequest.of(0, PREVIOUS_ACTUAL_LIMIT));
        Map<Long, List<SetlistSong>> songs = setlistRepository.findSongsIncludingDeleted(
                setlists.stream().map(Setlist::getId).toList());
        return setlists.stream()
                .map(s -> PreviousActualSetlistResponse.of(s, songs.getOrDefault(s.getId(), List.of())))
                .toList();
    }

    private List<SetlistResponse> currentList(Long performanceId) {
        List<Setlist> setlists = setlistRepository.findByPerformanceId(performanceId);
        // 곡은 삭제 여부와 관계없이 읽는다 - 저장된 셋리스트는 곡이 나중에 삭제돼도 그대로 보인다(F-2)
        Map<Long, List<SetlistSong>> songs = setlistRepository.findSongsIncludingDeleted(
                setlists.stream().map(Setlist::getId).toList());
        return setlists.stream()
                .sorted(Comparator.comparing(Setlist::getType))
                .map(s -> SetlistResponse.from(s, songs.getOrDefault(s.getId(), List.of())))
                .toList();
    }
}
