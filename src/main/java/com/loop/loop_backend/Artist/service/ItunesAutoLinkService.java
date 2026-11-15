package com.loop.loop_backend.Artist.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Song.Service.SongService;
import com.loop.loop_backend.infra.itunes.ItunesArtist;
import com.loop.loop_backend.infra.itunes.ItunesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * iTunes 미연결 아티스트 전체를 이름으로 검색해 1순위 후보에 자동 연결한다(itunesAutoLinked=true → 관리자 확인 대기).
 * 후보 이름이 같으면(대소문자·공백 무시) 확인 없이 확정(itunesAutoLinked=false)으로 저장한다.
 * 연결에 성공하면 곡 불러오기(미리듣기 URL 포함)까지 이어서 한다.
 * 관리자가 버튼으로 실행하고, 이미지 복사처럼 백그라운드로 돈다. 미연결만 대상이라 여러 번 실행해도 기존 연결은 안 바뀐다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ItunesAutoLinkService {

    private final ArtistRepository artistRepository;
    private final ItunesClient itunesClient;
    private final SongService songService;

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
                        boolean needsReview = !normalize(a.getName()).equals(normalize(top.artistName()));
                        if (artistRepository.autoLinkItunes(a.getId(), top.artistId(), top.artistLinkUrl(), top.artistName(), needsReview) == 1) {
                            linked++;
                            fetchSongs(a, failures);
                        } else skipped++; // 도는 사이 관리자가 직접 연결함
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

    // 곡 불러오기는 iTunes lookup을 2번(JP·US) 부르므로 그만큼 더 쉰다. 실패해도 연결은 유지하고 실패 목록에만 남긴다.
    private void fetchSongs(Artist a, List<Failure> failures) throws InterruptedException {
        Thread.sleep(delayMillis * 2);
        try {
            songService.fetchSongs(a.getId());
        } catch (Exception e) {
            failures.add(new Failure(a.getId(), a.getName(), "곡 불러오기 실패: "
                    + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())));
        }
    }

    private static String normalize(String name) {
        return name == null ? "" : name.replaceAll("\\s", "").toLowerCase(Locale.ROOT);
    }
}
