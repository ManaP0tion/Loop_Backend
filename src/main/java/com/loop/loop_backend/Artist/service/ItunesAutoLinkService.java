package com.loop.loop_backend.Artist.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.infra.itunes.ItunesArtist;
import com.loop.loop_backend.infra.itunes.ItunesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * iTunes 미연결 아티스트 전체를 이름으로 검색해 1순위 후보에 자동 연결한다(itunesAutoLinked=true → 관리자 확인 대기).
 * 관리자가 버튼으로 실행하고, 이미지 복사처럼 백그라운드로 돈다. 미연결만 대상이라 여러 번 실행해도 기존 연결은 안 바뀐다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ItunesAutoLinkService {

    private final ArtistRepository artistRepository;
    private final ItunesClient itunesClient;

    // ponytail: iTunes Search API 제한(분당 약 20회)에 맞춰 검색마다 3초 쉰다. 아티스트 300명이면 약 15분.
    // 막히면(403·429) 실패로 남고 다시 실행하면 남은 미연결만 이어서 돈다.
    long delayMillis = 3000;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Result lastResult;

    public record Failure(Long artistId, String name, String reason) {}

    /** finishedAt == null 이면 아직 실행 중. noCandidate = 검색 결과가 없어 연결하지 못한 아티스트 */
    public record Result(LocalDateTime startedAt, LocalDateTime finishedAt, int linked, int skipped,
                         List<Failure> noCandidate, List<Failure> failures) {}

    public boolean isRunning() {
        return running.get();
    }

    public Result getLastResult() {
        return lastResult;
    }

    // 다른 빈(컨트롤러)이 프록시를 통해 불러야 @Async가 적용된다.
    @Async("itunesAutoLinkExecutor")
    public void linkAllAsync() {
        if (!running.compareAndSet(false, true)) return;
        LocalDateTime startedAt = LocalDateTime.now();
        int linked = 0, skipped = 0;
        List<Failure> noCandidate = new ArrayList<>();
        List<Failure> failures = new ArrayList<>();
        lastResult = new Result(startedAt, null, 0, 0, List.of(), List.of());
        try {
            for (Artist a : artistRepository.findByItunesArtistIdIsNull()) {
                try {
                    List<ItunesArtist> candidates = itunesClient.searchArtists(a.getName());
                    if (candidates.isEmpty()) {
                        noCandidate.add(new Failure(a.getId(), a.getName(), "검색 결과 없음"));
                    } else {
                        ItunesArtist top = candidates.get(0);
                        if (artistRepository.autoLinkItunes(a.getId(), top.artistId(), top.artistLinkUrl(), top.artistName()) == 1) linked++;
                        else skipped++; // 도는 사이 관리자가 직접 연결함
                    }
                } catch (Exception e) {
                    failures.add(new Failure(a.getId(), a.getName(),
                            e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
                }
                Thread.sleep(delayMillis);
            }
            log.info("iTunes auto link completed: linked={}, skipped={}, noCandidate={}, failed={}",
                    linked, skipped, noCandidate.size(), failures.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lastResult = new Result(startedAt, LocalDateTime.now(), linked, skipped, noCandidate, failures);
            running.set(false);
        }
    }
}
