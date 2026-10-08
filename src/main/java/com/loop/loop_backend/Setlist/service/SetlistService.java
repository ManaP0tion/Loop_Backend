package com.loop.loop_backend.Setlist.service;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Setlist.domain.Setlist;
import com.loop.loop_backend.Setlist.domain.SetlistType;
import com.loop.loop_backend.Setlist.dto.SetlistResponse;
import com.loop.loop_backend.Setlist.dto.SetlistSaveRequest;
import com.loop.loop_backend.Setlist.event.SetlistResultSavedEvent;
import com.loop.loop_backend.Setlist.repository.SetlistRepository;
import com.loop.loop_backend.Song.Repository.SongRepository;
import com.loop.loop_backend.Song.domain.Song;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 셋리스트 관리(AD-07). 지난 셋리스트와 실제 셋리스트를 같은 방식(후보 곡 체크 + 순서)으로 저장한다.
 * 단독 공연 여부·헤더 필수값·곡 1곡 이상은 Setlist 엔티티가 검사한다(위반 시 400).
 */
@Service
@RequiredArgsConstructor
public class SetlistService {

    private final SetlistRepository setlistRepository;
    private final ConcertRepository concertRepository;
    private final SongRepository songRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 관리자 목록: 최근 공연 → 지난 내한 → 실제 순. */
    @Transactional(readOnly = true)
    public List<SetlistResponse> list(Long concertId) {
        findConcert(concertId);
        return currentList(concertId);
    }

    /** 유저용 지난 셋리스트(NO.67): 최근 공연 → 지난 내한, 최대 2건. 비공개 공연 403. */
    @Transactional(readOnly = true)
    public List<SetlistResponse> pastSetlists(Long concertId) {
        SetlistRules.requirePublished(findConcert(concertId));
        return currentList(concertId).stream().filter(s -> s.type().isPast()).toList();
    }

    /** 생성 또는 통째 교체. 응답은 그 공연의 셋리스트 전체. */
    @Transactional
    public List<SetlistResponse> save(Long concertId, SetlistType type, SetlistSaveRequest req) {
        Concert concert = findConcert(concertId);
        if (type == SetlistType.ACTUAL && concert.getExpectedSongCount() == null) {
            // 전체 적중률(득표 상위 n곡)의 기준이 없다
            throw new IllegalArgumentException("예상 곡 수가 없는 공연은 실제 셋리스트를 저장할 수 없다");
        }
        List<Song> songs = SetlistRules.resolveSongs(songRepository, concert, req.songIds());
        Setlist setlist = setlistRepository.findByConcertIdAndType(concertId, type)
                .map(existing -> {
                    existing.changeHeader(req.tourName(), req.performedOn(), req.venueName());
                    existing.replaceSongs(songs);
                    return existing;
                })
                .orElseGet(() -> setlistRepository.save(Setlist.builder()
                        .concert(concert).type(type)
                        .tourName(req.tourName()).performedOn(req.performedOn()).venueName(req.venueName())
                        .songs(songs)
                        .build()));
        // 결과 메일은 실제 셋리스트 최초 저장 때만(NO.69). 수정은 적중률만 바뀐다(조회 시 계산)
        if (setlist.markResultMailSent(LocalDateTime.now(SetlistRules.ZONE_KST))) {
            eventPublisher.publishEvent(new SetlistResultSavedEvent(concertId, concert.getTitle()));
        }
        setlistRepository.flush();
        return currentList(concertId);
    }

    /** 지난 셋리스트만 지울 수 있다. 실제 셋리스트는 결과 메일 기록(최초 1회)이 함께 지워지므로 수정(PUT)만 허용 - 400. */
    @Transactional
    public List<SetlistResponse> delete(Long concertId, SetlistType type) {
        findConcert(concertId);
        if (!type.isPast()) {
            throw new IllegalArgumentException("실제 셋리스트는 삭제할 수 없다 - 수정으로 교체한다");
        }
        Setlist setlist = setlistRepository.findByConcertIdAndType(concertId, type)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETLIST_NOT_FOUND));
        setlistRepository.delete(setlist);
        setlistRepository.flush();
        return currentList(concertId);
    }

    private List<SetlistResponse> currentList(Long concertId) {
        return setlistRepository.findByConcertId(concertId).stream()
                .sorted(Comparator.comparing(Setlist::getType))
                .map(SetlistResponse::from)
                .toList();
    }

    private Concert findConcert(Long concertId) {
        return concertRepository.findById(concertId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));
    }
}
