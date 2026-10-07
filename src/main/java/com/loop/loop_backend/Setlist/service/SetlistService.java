package com.loop.loop_backend.Setlist.service;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Setlist.domain.Setlist;
import com.loop.loop_backend.Setlist.domain.SetlistType;
import com.loop.loop_backend.Setlist.dto.SetlistResponse;
import com.loop.loop_backend.Setlist.dto.SetlistSaveRequest;
import com.loop.loop_backend.Setlist.repository.SetlistRepository;
import com.loop.loop_backend.Song.Repository.SongRepository;
import com.loop.loop_backend.Song.domain.Song;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /** 관리자 목록: 최근 공연 → 지난 내한 → 실제 순. */
    @Transactional(readOnly = true)
    public List<SetlistResponse> list(Long concertId) {
        findConcert(concertId);
        return currentList(concertId);
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
        setlistRepository.findByConcertIdAndType(concertId, type).ifPresentOrElse(
                setlist -> {
                    setlist.changeHeader(req.tourName(), req.performedOn(), req.venueName());
                    setlist.replaceSongs(songs);
                },
                () -> setlistRepository.save(Setlist.builder()
                        .concert(concert).type(type)
                        .tourName(req.tourName()).performedOn(req.performedOn()).venueName(req.venueName())
                        .songs(songs)
                        .build()));
        setlistRepository.flush();
        return currentList(concertId);
    }

    @Transactional
    public List<SetlistResponse> delete(Long concertId, SetlistType type) {
        findConcert(concertId);
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
