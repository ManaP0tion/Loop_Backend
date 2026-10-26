package com.loop.loop_backend.ConcertImport.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.ConcertImport.domain.ConcertImport;
import com.loop.loop_backend.ConcertImport.domain.ImportStatus;
import com.loop.loop_backend.ConcertImport.dto.KopisSearchItem;
import com.loop.loop_backend.ConcertImport.dto.KopisSearchResponse;
import com.loop.loop_backend.ConcertImport.dto.KopisSearchStatus;
import com.loop.loop_backend.ConcertImport.dto.ManualImportRequest;
import com.loop.loop_backend.ConcertImport.dto.ManualImportResponse;
import com.loop.loop_backend.ConcertImport.kopis.KopisClient;
import com.loop.loop_backend.ConcertImport.kopis.KopisPerformance;
import com.loop.loop_backend.ConcertImport.kopis.PerformanceIdentifier;
import com.loop.loop_backend.ConcertImport.repository.ConcertImportRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * KOPIS 직접 등록: 자동 수집 필터에 안 걸린 공연을 관리자가 공연명으로 찾아(search) 등록한다(register).
 * 등록은 검토 대기열 행을 만들거나 다시 쓴 뒤 바로 승인한다 - 공연 생성 규칙은 기존 승인(ConcertImportService.publish)과 같다.
 */
@Service
@RequiredArgsConstructor
public class ConcertManualImportService {

    // 검색 기간(오늘 기준). 지난 공연도 셋리스트·후기용으로 등록할 수 있게 과거를 더 길게 본다. 바꿀 때는 이 값만 고친다.
    static final int SEARCH_PAST_YEARS = 2;
    static final int SEARCH_FUTURE_YEARS = 1;
    static final int MAX_SEARCH_RESULTS = 200;

    private final KopisClient kopisClient;
    private final ConcertRepository concertRepository;
    private final ConcertImportRepository importRepository;
    private final ArtistRepository artistRepository;
    private final ConcertImportService concertImportService;
    private final PerformanceIdentifier identifier;

    /**
     * 공연명 검색 + 결과마다 우리 쪽 등록 상태. KOPIS 응답을 기다리는 동안 DB 연결을 잡지 않도록 트랜잭션을 걸지 않는다.
     */
    public KopisSearchResponse search(String keyword) {
        if (keyword == null || keyword.isBlank()) throw new BusinessException(ErrorCode.INVALID_INPUT);

        LocalDate today = LocalDate.now();
        KopisClient.SearchResult result = kopisClient.searchPerformances(keyword.trim(),
                today.minusYears(SEARCH_PAST_YEARS), today.plusYears(SEARCH_FUTURE_YEARS), MAX_SEARCH_RESULTS);

        Set<String> kopisIds = result.performances().stream()
                .map(KopisPerformance::getKopisId).collect(Collectors.toSet());
        if (kopisIds.isEmpty()) return new KopisSearchResponse(List.of(), result.truncated());

        // 상태 계산용 조회 2번(공연, 검토 대기열). 결과 건수만큼 쿼리가 늘지 않게 IN으로 한 번에 가져온다.
        Map<String, List<Long>> concertIdsByKopisId = concertRepository.findByKopisIdIn(kopisIds).stream()
                .collect(Collectors.groupingBy(Concert::getKopisId,
                        Collectors.mapping(Concert::getId, Collectors.toList())));
        Map<String, Set<ImportStatus>> importStatusesByKopisId = importRepository.findByKopisIdIn(kopisIds).stream()
                .collect(Collectors.groupingBy(ConcertImport::getKopisId,
                        Collectors.mapping(ConcertImport::getStatus, Collectors.toSet())));
        // 추천 아티스트: 대기열 자동 매칭과 같은 공연명 규칙. 자동 수집을 꺼 둔 아티스트 공연이 직접 등록의 주 대상이라
        // 자동 수집 여부와 상관없이 J-POP 아티스트 전체와 비교한다. 출연진 매칭은 결과마다 KOPIS 상세가 필요해 하지 않는다.
        List<Artist> jpopArtists = artistRepository.findByCategory(ConcertCategory.J_POP_ARTIST);

        List<KopisSearchItem> items = result.performances().stream()
                .sorted(Comparator.comparing(KopisPerformance::getStartDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(p -> {
                    List<Long> concertIds = concertIdsByKopisId.getOrDefault(p.getKopisId(), List.of());
                    KopisSearchStatus status = statusOf(concertIds,
                            importStatusesByKopisId.getOrDefault(p.getKopisId(), Set.of()));
                    List<KopisSearchItem.SuggestedArtist> suggested = identifier.matchArtistsByTitle(p.getTitle(), jpopArtists)
                            .stream().map(a -> new KopisSearchItem.SuggestedArtist(a.getId(), a.getName())).toList();
                    return new KopisSearchItem(p.getKopisId(), p.getTitle(), p.getPosterUrl(), p.getVenue(),
                            p.getStartDate(), p.getEndDate(), status, concertIds, suggested);
                })
                .toList();
        return new KopisSearchResponse(items, result.truncated());
    }

    /**
     * 공연이 있으면 등록됨. 없으면 검토 대기열 상태로 본다 - 대기 > 거절 순.
     * 승인됐는데 공연이 없는 행(승인 후 삭제)은 아무 기록이 없는 것과 같아 NEW다(등록하면 다시 열린다).
     */
    private KopisSearchStatus statusOf(List<Long> concertIds, Set<ImportStatus> importStatuses) {
        if (!concertIds.isEmpty()) return KopisSearchStatus.REGISTERED;
        if (importStatuses.contains(ImportStatus.PENDING)) return KopisSearchStatus.PENDING;
        if (importStatuses.contains(ImportStatus.REJECTED)) return KopisSearchStatus.REJECTED;
        return KopisSearchStatus.NEW;
    }

    /**
     * 직접 등록. 요청 검증과 중복 검사를 KOPIS 조회보다 먼저 해서, 어차피 실패할 요청에 KOPIS를 부르지 않는다.
     * KOPIS 상세는 1번만 조회하고, 그 응답으로 대기열 행과 공연을 함께 채운다.
     */
    @Transactional
    public ManualImportResponse register(ManualImportRequest request) {
        ConcertCategory category = request.category();
        if (!Concert.isSelectableCategory(category)) throw new BusinessException(ErrorCode.INVALID_INPUT);
        Artist artist = resolveArtist(category, request.artistIdsOrEmpty());

        String kopisId = request.kopisId().trim();
        // 아티스트가 달라도 같은 KOPIS 공연이면 막는다 - (kopisId, 아티스트) 기준이면 아티스트 없이 한 번 더 들어갈 수 있다.
        if (concertRepository.existsByKopisId(kopisId)) throw new BusinessException(ErrorCode.CONCERT_ALREADY_REGISTERED);

        KopisClient.ImportSource source = kopisClient.getPerformanceForImport(kopisId);
        KopisPerformance perf = source.performance();

        // 자동 수집과 같은 키(kopisId, 아티스트)로 기존 행을 찾는다. 있으면 상태와 상관없이 다시 써서
        // 같은 공연·아티스트 행이 둘 생기지 않게 한다(위에서 공연이 없음을 확인했으니 승인된 행도 공연이 삭제된 경우다).
        Optional<ConcertImport> existing = artist == null
                ? importRepository.findByKopisIdAndMatchedArtistIsNull(kopisId)
                : importRepository.findByKopisIdAndMatchedArtist_Id(kopisId, artist.getId());
        // 새 행은 키만 정해 만들고, 기존 행과 똑같이 resetForManualImport로 KOPIS 값을 채운다.
        ConcertImport imp = existing.orElseGet(() -> ConcertImport.builder()
                .kopisId(kopisId)
                .matchedArtist(artist)
                .build());
        imp.resetForManualImport(perf.getTitle(), perf.getPosterUrl(), perf.getVenue(),
                perf.getStartDate(), perf.getEndDate(), category);
        importRepository.save(imp);

        Concert concert = concertImportService.publish(imp, source.detail());
        return ManualImportResponse.of(imp.getId(), concert);
    }

    /** 페스티벌은 아티스트 없이(라인업으로 관리), 내한 공연은 0~1명. */
    private Artist resolveArtist(ConcertCategory category, List<Long> artistIds) {
        if (category == ConcertCategory.JAPAN_FESTIVAL && !artistIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (artistIds.size() > 1) throw new BusinessException(ErrorCode.INVALID_INPUT);
        if (artistIds.isEmpty()) return null;
        Long artistId = artistIds.get(0);
        if (artistId == null) throw new BusinessException(ErrorCode.INVALID_INPUT);
        return artistRepository.findById(artistId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTIST_NOT_FOUND));
    }
}
