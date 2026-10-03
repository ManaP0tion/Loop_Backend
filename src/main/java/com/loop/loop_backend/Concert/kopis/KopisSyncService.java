package com.loop.loop_backend.Concert.kopis;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * KOPIS 공연을 1차 필터링(아티스트/페스티벌 매칭)해 검토 대기(ConcertImport)로 적재한다.
 * 운영 데이터(Concert)에는 직접 쓰지 않는다 — 관리자 승인을 거쳐야 사이트에 노출된다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KopisSyncService {

    private final ArtistRepository artistRepository;
    private final KopisClient kopisClient;
    private final PerformanceIdentifier identifier;
    private final ConcertImportWriter writer;

    // 싱크가 돌고 있는지. 스케줄러와 수동 실행이 겹쳐도 한 번만 돌게 한다 (동시에 돌면 KOPIS 요청이 두 배로 몰린다).
    private final AtomicBoolean running = new AtomicBoolean(false);

    public boolean isRunning() {
        return running.get();
    }

    // 수동 실행용: 별도 스레드에서 돌려서 호출한 쪽은 바로 돌아온다 (싱크가 몇 분 걸려 프록시 타임아웃이 나는 것 방지).
    // 다른 빈(컨트롤러)이 프록시를 통해 불러야 @Async가 적용된다.
    @Async("kopisSyncExecutor")
    public void syncAllAsync() {
        syncAll();
    }

    // 스케줄러용: 현재 스레드에서 끝날 때까지 실행한다. 이미 돌고 있으면 건너뛴다.
    public void syncAll() {
        if (!running.compareAndSet(false, true)) {
            log.warn("KOPIS sync skipped: already running");
            return;
        }
        try {
            runSync();
        } finally {
            running.set(false); // 예외가 나도 다음 실행을 막지 않게 반드시 푼다
        }
    }

    private void runSync() {
        // V2는 J-POP 공연만 수집한다. Repository 조회 방식은 쿼리 최적화 작업에서 다룰 예정이라 여기서 메모리로 거른다.
        List<Artist> artists = artistRepository.findByAutoFetchConcertsTrue().stream()
                .filter(a -> a.getCategory() == ConcertCategory.J_POP_ARTIST)
                .toList();
        List<KopisPerformance> allPerfs = kopisClient.getAllUpcomingPerformances();
        log.info("KOPIS sync: {} performances × {} artists", allPerfs.size(), artists.size());

        for (KopisPerformance perf : allPerfs) {
            try {
                processPerformance(perf, artists);
            } catch (Exception e) {
                log.error("Failed to process '{}': {}", perf.getTitle(), e.getMessage());
            }
        }
        log.info("KOPIS sync completed");
    }

    // 공연 1건 처리 흐름: 제목으로 식별 → (실패 시) 출연진으로 식별 → 수집 대상이면 검토 큐에 저장
    private void processPerformance(KopisPerformance perf, List<Artist> artists) {
        IdentificationResult result = identifier.identifyByTitle(perf.getTitle(), artists);

        if (!result.isMatched()) {
            result = identifyByCast(perf, artists);
        }

        if (result.isMatched()) {
            writer.saveImport(perf, result);
        }
    }

    // 출연진 식별 단계: 상세 조회가 필요한 공연인지 판단 → KOPIS 상세에서 출연진 조회 → 출연진으로 식별
    private IdentificationResult identifyByCast(KopisPerformance perf, List<Artist> artists) {
        if (!identifier.shouldFetchCast(perf.getTitle())) {
            return IdentificationResult.notMatched();
        }
        String cast = kopisClient.getPerformanceCast(perf.getKopisId());
        return identifier.matchCast(cast, artists);
    }
}
