package com.loop.loop_backend.Song.Service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Song.DTO.SongFetchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * iTunes에 연결된 아티스트 전체의 곡을 다시 불러온다(미리듣기 URL 채우기 등).
 * iTunes 자동 연결과 같은 실행기(스레드 1개, 대기열 없음)를 써서 두 작업이 동시에 돌지 않는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SongBulkFetchService {

    private final ArtistRepository artistRepository;
    private final SongService songService;

    // 곡 불러오기 1번에 iTunes lookup 2번(JP·US). 분당 약 20회 제한에 맞춰 아티스트마다 6초 쉰다. 300명이면 약 30분.
    long delayMillis = 6000;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Result lastResult;

    public record Failure(Long artistId, String name, String reason) {}

    /** finishedAt == null 이면 아직 실행 중. done/total로 진행률을 본다 */
    public record Result(LocalDateTime startedAt, LocalDateTime finishedAt, int total, int done,
                         int created, int updated, List<Failure> failures) {}

    public boolean isRunning() {
        return running.get();
    }

    public Result getLastResult() {
        return lastResult;
    }

    // 다른 빈(컨트롤러)이 프록시를 통해 불러야 @Async가 적용된다.
    @Async("itunesAutoLinkExecutor")
    public void fetchAllAsync() {
        if (!running.compareAndSet(false, true)) return;
        LocalDateTime startedAt = LocalDateTime.now();
        List<Artist> artists = artistRepository.findByItunesArtistIdIsNotNullOrderByIdAsc();
        int done = 0, created = 0, updated = 0;
        List<Failure> failures = new ArrayList<>();
        try {
            for (Artist a : artists) {
                lastResult = new Result(startedAt, null, artists.size(), done, created, updated, List.copyOf(failures));
                try {
                    SongFetchResult r = songService.fetchSongs(a.getId());
                    created += r.created();
                    updated += r.updated();
                } catch (Exception e) {
                    failures.add(new Failure(a.getId(), a.getName(),
                            e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
                }
                done++;
                Thread.sleep(delayMillis);
            }
            log.info("iTunes song fetch all completed: artists={}, created={}, updated={}, failed={}",
                    artists.size(), created, updated, failures.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lastResult = new Result(startedAt, LocalDateTime.now(), artists.size(), done, created, updated, failures);
            running.set(false);
        }
    }
}
