package com.loop.loop_backend.ConcertImport.kopis;

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

        // KOPIS 목록 API: 오늘~1년 뒤 대중음악 공연. 목록에는 출연진이 없다.
        List<KopisPerformance> allPerfs = kopisClient.getAllUpcomingPerformances();
        log.info("KOPIS sync: {} performances × {} artists", allPerfs.size(), artists.size());

        // 공연 1건이 실패해도 전체 싱크가 멈추지 않도록 건별로 처리한다.
        SyncStats stats = new SyncStats();
        for (KopisPerformance perf : allPerfs) {
            try {
                processPerformance(perf, artists, stats);
            } catch (Exception e) {
                stats.failed++;
                log.error("Failed to process '{}'", perf.getTitle(), e);
            }
        }
        log.info("KOPIS sync completed: {}", stats);
    }

    // 공연 1건 처리 흐름: 제목으로 식별 → (실패 시) 출연진으로 식별 → 수집 대상이면 검토 큐에 저장
    // 식별은 identifier가, 출연진 조회(KOPIS 상세 API)만 여기서 한다.
    private void processPerformance(KopisPerformance perf, List<Artist> artists, SyncStats stats) {
        // 제목으로 식별 (API 호출 없음)
        IdentificationResult result = identifier.identifyByTitle(perf.getTitle(), artists);

        // 제목으로 못 찾으면 출연진으로 식별. 상세 API는 공연마다 호출되므로 일본 신호가 있는 공연만 조회한다.
        // 출연진 매칭은 실효성 검토 중(실측 매칭 0건) — 제거 시 이 블록과 identifier.shouldFetchCast/matchCast를 함께 지운다.
        if (!result.isMatched() && identifier.shouldFetchCast(perf.getTitle())) {
            String cast = kopisClient.getPerformanceCast(perf.getKopisId());
            result = identifier.matchCast(cast, artists);
        }

        if (result.isMatched()) {
            stats.record(result.matchReason(), writer.saveImport(perf, result));
        }
    }

    // 싱크 1회의 집계. 완료 로그 한 줄로 새로 들어온 건지, 기존 행을 갱신한 건지 구분할 수 있게 한다.
    private static final class SyncStats {
        private int title;
        private int cast;
        private int festival;
        private int failed;
        private ImportSaveResult saved = ImportSaveResult.NONE;

        void record(String matchReason, ImportSaveResult result) {
            switch (matchReason) {
                case IdentificationResult.TITLE_MATCH -> title++;
                case IdentificationResult.CAST_MATCH -> cast++;
                case IdentificationResult.JAPAN_FESTIVAL -> festival++;
                default -> { }
            }
            saved = saved.plus(result);
        }

        @Override
        public String toString() {
            return String.format("matched %d (title %d, cast %d, festival %d) → created %d, updated %d, skipped %d / failed %d",
                    title + cast + festival, title, cast, festival,
                    saved.created(), saved.updated(), saved.skipped(), failed);
        }
    }
}
