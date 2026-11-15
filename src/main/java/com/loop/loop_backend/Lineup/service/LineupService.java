package com.loop.loop_backend.Lineup.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Lineup.dto.LineupItemResponse;
import com.loop.loop_backend.Lineup.dto.LineupAddRequest;
import com.loop.loop_backend.Lineup.dto.LineupPatchRequests.Direction;
import com.loop.loop_backend.Lineup.dto.LineupResponse;
import com.loop.loop_backend.Performance.domain.Performance;
import com.loop.loop_backend.Performance.repository.PerformanceRepository;
import com.loop.loop_backend.Setlist.service.SetlistDataGuard;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.stream.IntStream;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 라인업 관리(AD-04, BE-13)와 유저용 조회(BE-14).
 * 관리자 변경은 모두 즉시 저장되고, 화면을 다시 그리기 쉽게 항상 공연의 라인업 전체를 돌려준다.
 * 라인업 항목 = 페스티벌의 출연 정보(Performance). lineupId는 performanceId와 같다.
 * 페스티벌 여부·DAY 범위 검사는 Performance 엔티티가 한다(위반 시 400).
 * 셋리스트·투표가 붙은 헤드라이너는 삭제·헤드라이너 해제를 막는다(409).
 */
@Service
@RequiredArgsConstructor
public class LineupService {

    static final int MAX_HEADLINERS = 3;

    private final PerformanceRepository performanceRepository;
    private final ConcertRepository concertRepository;
    private final ArtistRepository artistRepository;
    private final SetlistDataGuard setlistDataGuard;

    /**
     * 유저용 라인업(비로그인 열람). 비공개(오픈 예정) 공연은 상세와 같이 403.
     * 라인업이 일부만 등록됐거나 없어도 정상 응답(NO.50), 공연이 끝난 뒤에도 그대로 보인다.
     */
    @Transactional(readOnly = true)
    public LineupResponse publicLineup(Long concertId) {
        Concert concert = findConcert(concertId);
        if (!concert.isPublished()) {
            throw new BusinessException(ErrorCode.CONCERT_NOT_OPEN);
        }
        if (!concert.isFestival()) {
            return new LineupResponse(List.of(), List.of());
        }
        List<LineupResponse.Day> days = IntStream.rangeClosed(1, Performance.dayCount(concert))
                .mapToObj(day -> new LineupResponse.Day(day, concert.getStartDate().plusDays(day - 1)))
                .toList();
        return new LineupResponse(days, currentList(concertId));
    }

    @Transactional(readOnly = true)
    public List<LineupItemResponse> list(Long concertId) {
        findConcert(concertId);
        return currentList(concertId);
    }

    /**
     * DB 선택(artistIds) 또는 직접 입력(name) 중 하나. 공연에 이미 있는 아티스트면 출연 DAY만 늘리고(셋리스트·투표는 아티스트당 하나),
     * 새 아티스트는 요청 순서대로 맨 뒤에 붙는다.
     */
    @Transactional
    public List<LineupItemResponse> add(Long concertId, LineupAddRequest req) {
        Concert concert = findConcert(concertId);
        List<Artist> artists = artistsToAdd(req);
        Map<Long, Performance> existing = new HashMap<>();
        for (Artist artist : artists) {
            performanceRepository.findByConcertIdAndArtistId(concertId, artist.getId())
                    .ifPresent(p -> existing.put(artist.getId(), p));
        }

        // 하나라도 그 DAY에 이미 있으면 아무것도 넣지 않는다
        for (Performance performance : existing.values()) {
            if (performance.hasDay(req.day())) {
                throw new BusinessException(ErrorCode.DUPLICATE_LINEUP);
            }
        }
        int order = performanceRepository.findMaxDisplayOrder(concertId);
        for (Artist artist : artists) {
            Performance performance = existing.get(artist.getId());
            if (performance != null) {
                performance.addDay(req.day());
            } else {
                performanceRepository.save(Performance.builder()
                        .concert(concert).artist(artist).day(req.day()).displayOrder(++order)
                        .build());
            }
        }
        performanceRepository.flush();
        return currentList(concertId);
    }

    /** 출연 DAY 목록 통째 교체. 빈 목록·범위 밖 DAY는 400(Performance가 검사). 셋리스트·투표는 그대로 남는다. */
    @Transactional
    public List<LineupItemResponse> replaceDays(Long concertId, Long lineupId, List<Integer> days) {
        findLineup(concertId, lineupId).replaceDays(days);
        performanceRepository.flush();
        return currentList(concertId);
    }

    @Transactional
    public List<LineupItemResponse> delete(Long concertId, Long lineupId) {
        Performance lineup = findLineup(concertId, lineupId);
        setlistDataGuard.requireNone(lineup.getId());
        performanceRepository.delete(lineup);
        performanceRepository.flush();
        return currentList(concertId);
    }

    /** 바로 앞/뒤 항목과 노출 순서를 맞바꾼다. 맨 앞 UP·맨 뒤 DOWN은 변화 없음. */
    @Transactional
    public List<LineupItemResponse> move(Long concertId, Long lineupId, Direction direction) {
        findConcert(concertId);
        List<Performance> lineups = performanceRepository.findByConcertIdOrderByDisplayOrderAsc(concertId);
        int index = indexOf(lineups, lineupId);
        int neighbor = direction == Direction.UP ? index - 1 : index + 1;
        if (neighbor >= 0 && neighbor < lineups.size()) {
            lineups.get(index).swapOrderWith(lineups.get(neighbor));
        }
        performanceRepository.flush();
        return currentList(concertId);
    }

    /** 헤드라이너 해제는 셋리스트·투표가 없을 때만(409). 지정은 페스티벌당 최대 3팀(400) - 예상 셋리스트를 3아티스트로 나눠 보여준다. */
    @Transactional
    public List<LineupItemResponse> changeHeadliner(Long concertId, Long lineupId, boolean headliner) {
        Performance lineup = findLineup(concertId, lineupId);
        if (!headliner) setlistDataGuard.requireNone(lineup.getId());
        if (headliner && !lineup.isHeadliner() && performanceRepository.findByConcertId(concertId).stream()
                .filter(Performance::isHeadliner).count() >= MAX_HEADLINERS) {
            throw new IllegalArgumentException("헤드라이너는 최대 " + MAX_HEADLINERS + "팀");
        }
        lineup.changeHeadliner(headliner);
        return currentList(concertId);
    }

    /** 헤드라이너 예상 곡 수(n). 헤드라이너가 아니면 셋리스트를 운영하지 않으므로 400. null이면 비움(기본 20곡). */
    @Transactional
    public List<LineupItemResponse> changeExpectedSongCount(Long concertId, Long lineupId, Integer expectedSongCount) {
        Performance lineup = findLineup(concertId, lineupId);
        if (!lineup.isHeadliner()) {
            throw new IllegalArgumentException("예상 곡 수는 헤드라이너에만 정한다");
        }
        lineup.changeExpectedSongCount(expectedSongCount);
        return currentList(concertId);
    }

    /**
     * 동행 프로필 '꼭 보고 싶은 무대' 검증(NO.27). 고른 라인업 항목이 모두 이 공연 소속이고 그 DAY에 출연해야 한다.
     * 아무것도 안 골랐으면 통과(미선택 허용). 다른 DAY·다른 공연·없는 항목이 섞이면 400.
     */
    @Transactional(readOnly = true)
    public void validateStages(Long concertId, int day, List<Long> lineupIds) {
        if (lineupIds == null || lineupIds.isEmpty()) return;
        List<Long> ids = lineupIds.stream().distinct().toList();
        if (performanceRepository.countByIdInAndConcertIdAndDay(ids, concertId, day) != ids.size()) {
            throw new IllegalArgumentException("이 공연·DAY 라인업에 없는 무대가 있다");
        }
    }

    private List<Artist> artistsToAdd(LineupAddRequest req) {
        boolean bySelect = req.artistIds() != null && !req.artistIds().isEmpty();
        boolean byInput = req.name() != null && !req.name().isBlank();
        if (bySelect == byInput) {
            throw new IllegalArgumentException("artistIds와 name 중 하나만 보내야 한다");
        }
        if (byInput) {
            // 직접 입력한 아티스트는 아티스트 DB에도 남아 다음 페스티벌부터 DB 선택으로 고를 수 있다
            return List.of(artistRepository.save(Artist.builder()
                    .name(req.name().trim())
                    .imageUrl(blankToNull(req.imageUrl()))
                    .nationality(req.nationality())
                    .build()));
        }
        List<Long> ids = req.artistIds().stream().distinct().toList();
        Map<Long, Artist> found = artistRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Artist::getId, Function.identity()));
        if (found.size() != ids.size()) {
            throw new BusinessException(ErrorCode.ARTIST_NOT_FOUND);
        }
        return ids.stream().map(found::get).toList();
    }

    private List<LineupItemResponse> currentList(Long concertId) {
        return performanceRepository.findByConcertIdOrderByDisplayOrderAsc(concertId).stream()
                .map(LineupItemResponse::from)
                .toList();
    }

    private Concert findConcert(Long concertId) {
        return concertRepository.findById(concertId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));
    }

    /** 경로의 공연에 속한 항목만 찾는다 - 다른 공연의 lineupId면 404. */
    private Performance findLineup(Long concertId, Long lineupId) {
        findConcert(concertId);
        return performanceRepository.findById(lineupId)
                .filter(l -> l.getConcert().getId().equals(concertId))
                .orElseThrow(() -> new BusinessException(ErrorCode.LINEUP_NOT_FOUND));
    }

    private static int indexOf(List<Performance> lineups, Long lineupId) {
        for (int i = 0; i < lineups.size(); i++) {
            if (lineups.get(i).getId().equals(lineupId)) return i;
        }
        throw new BusinessException(ErrorCode.LINEUP_NOT_FOUND);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
