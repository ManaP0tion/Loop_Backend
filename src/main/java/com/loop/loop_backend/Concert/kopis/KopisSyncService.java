package com.loop.loop_backend.Concert.kopis;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.domain.ConcertImport;
import com.loop.loop_backend.Concert.domain.ImportStatus;
import com.loop.loop_backend.Concert.repository.ConcertImportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * KOPIS 공연을 1차 필터링(아티스트/페스티벌 매칭)해 검토 대기(ConcertImport)로 적재한다.
 * 운영 데이터(Concert)에는 직접 쓰지 않는다 — 관리자 승인을 거쳐야 사이트에 노출된다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KopisSyncService {

    private static final int ALIAS_MIN_LENGTH = 4;

    private final ArtistRepository artistRepository;
    private final ConcertImportRepository concertImportRepository;
    private final KopisClient kopisClient;

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

    @Transactional
    public void processPerformance(KopisPerformance perf, List<Artist> artists) {
        // 0단계: 일본 페스티벌 자동 인식 → artist=null, JAPAN_FESTIVAL로 저장
        if (JapanFestivalMatcher.isJapanFestival(perf.getTitle())) {
            upsertJapanFestival(perf);
            return;
        }

        // 1단계: 타이틀에서 아티스트 매칭 (API 추가 호출 없음)
        String reason = "TITLE_MATCH";
        List<Artist> matched = artists.stream()
                .filter(a -> nameContainedInTitle(perf.getTitle(), a))
                .collect(Collectors.toList());

        if (matched.isEmpty()) {
            // 2단계: 타이틀 미매칭 + 일본/투어 신호가 있을 때만 상세 API로 출연진 확인
            if (!shouldFetchCast(perf.getTitle())) return;
            String cast = kopisClient.getPerformanceCast(perf.getKopisId());
            if (cast == null) return;
            reason = "CAST_MATCH";
            matched = artists.stream()
                    .filter(a -> castMatches(cast, a))
                    .collect(Collectors.toList());
        }

        for (Artist artist : matched) {
            upsert(artist, perf, reason);
        }
    }

    private void upsert(Artist artist, KopisPerformance perf, String reason) {
        ConcertCategory category = artist.getCategory() != null
                ? artist.getCategory()
                : ConcertCategory.J_POP_ARTIST;
        upsertImport(artist, perf, category, reason,
                concertImportRepository.findByKopisIdAndMatchedArtist_Id(perf.getKopisId(), artist.getId()));
    }

    private void upsertJapanFestival(KopisPerformance perf) {
        upsertFestival(perf, ConcertCategory.JAPAN_FESTIVAL, "JAPAN_FESTIVAL");
    }

    private void upsertFestival(KopisPerformance perf, ConcertCategory category, String reason) {
        upsertImport(null, perf, category, reason,
                concertImportRepository.findByKopisIdAndMatchedArtistIsNull(perf.getKopisId()));
    }

    /**
     * 재수집 멱등성:
     * - 신규 → PENDING import 저장
     * - 기존 PENDING → 원본 필드만 최신화 (아직 검토 전)
     * - 기존 APPROVED/REJECTED → skip (이미 처리한 건을 검토 큐에 되살리지 않음)
     */
    private void upsertImport(Artist artist, KopisPerformance perf, ConcertCategory category,
                              String reason, Optional<ConcertImport> existing) {
        existing.ifPresentOrElse(
                imp -> {
                    if (imp.getStatus() == ImportStatus.PENDING) {
                        imp.updateFromKopis(perf.getTitle(), perf.getPosterUrl(), perf.getVenue(),
                                perf.getStartDate(), perf.getEndDate(), category, reason);
                    }
                },
                () -> concertImportRepository.save(ConcertImport.builder()
                        .matchedArtist(artist)
                        .kopisId(perf.getKopisId())
                        .title(perf.getTitle())
                        .posterUrl(perf.getPosterUrl())
                        .venue(perf.getVenue())
                        .startDate(perf.getStartDate())
                        .endDate(perf.getEndDate())
                        .suggestedCategory(category)
                        .matchReason(reason)
                        .status(ImportStatus.PENDING)
                        .build())
        );
    }

    /**
     * 타이틀에 아티스트 name / nameKo / 4자 이상의 nameAlias가 단어 단위로 포함되면 true.
     * 3자 이하의 짧은 alias(예: "미세스")는 일반 단어와 충돌해 오탐을 유발하므로 배제한다.
     */
    private boolean nameContainedInTitle(String title, Artist artist) {
        if (title == null) return false;

        if (wordBoundaryMatch(title, artist.getName())) return true;

        if (artist.getNameKo() != null) {
            boolean koHit = Arrays.stream(artist.getNameKo().split(" / "))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .anyMatch(ko -> wordBoundaryMatch(title, ko));
            if (koHit) return true;
        }

        String alias = artist.getNameAlias();
        if (alias != null && alias.length() >= ALIAS_MIN_LENGTH) {
            return wordBoundaryMatch(title, alias);
        }

        return false;
    }

    /**
     * keyword가 text 안에 단어 경계(앞뒤 문자가 문자/숫자가 아님)로 존재하면 true.
     * "이브"가 "라이브" 안에 묻히는 경우, "아도"가 "아도가" 조사에 붙는 경우를 차단한다.
     */
    private boolean wordBoundaryMatch(String text, String keyword) {
        if (text == null || keyword == null || keyword.isEmpty()) return false;
        String textLower = text.toLowerCase();
        String keyLower = keyword.toLowerCase();
        int keyLen = keyword.length();
        int idx = 0;
        while ((idx = textLower.indexOf(keyLower, idx)) >= 0) {
            boolean startOk = (idx == 0) || !Character.isLetterOrDigit(text.charAt(idx - 1));
            boolean endOk = (idx + keyLen >= text.length()) || !Character.isLetterOrDigit(text.charAt(idx + keyLen));
            if (startOk && endOk) return true;
            idx++;
        }
        return false;
    }

    /**
     * cast(콤마 구분)의 각 토큰이 아티스트 name / nameKo / nameAlias 중 하나와
     * 공백·특수문자를 무시한 lowercase 상태에서 정확 일치하면 true.
     */
    private boolean castMatches(String cast, Artist artist) {
        if (cast == null) return false;
        String[] tokens = cast.split(",");
        for (String rawToken : tokens) {
            String token = normalize(rawToken);
            if (token.isEmpty()) continue;
            if (token.equals(normalize(artist.getName()))) return true;
            if (artist.getNameKo() != null) {
                for (String ko : artist.getNameKo().split(" / ")) {
                    if (token.equals(normalize(ko))) return true;
                }
            }
            if (artist.getNameAlias() != null && token.equals(normalize(artist.getNameAlias()))) {
                return true;
            }
        }
        return false;
    }

    /** 문자·숫자만 남기고 lowercase 반환. null 안전. */
    private String normalize(String s) {
        if (s == null) return "";
        return s.replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase();
    }

    /**
     * 타이틀에 일본/투어 관련 신호가 있을 때만 상세 API(prfcast)를 조회한다.
     * KOPIS 상세 응답의 cast 필드는 대부분 공백이라 무분별한 조회는 낭비이므로,
     * 국내 아티스트가 명확한 공연은 스킵한다.
     */
    private boolean shouldFetchCast(String title) {
        if (title == null) return false;
        String upper = title.toUpperCase();
        if (upper.contains("JAPAN") || upper.contains("ASIA TOUR")) return true;
        if (title.contains("일본") || title.contains("재팬")
                || title.contains("아시아 투어") || title.contains("내한")) return true;
        return containsJapaneseChar(title);
    }

    /** 히라가나·카타카나·한자 유니코드 블록 중 하나라도 포함되면 true. */
    private boolean containsJapaneseChar(String text) {
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(text.charAt(i));
            if (block == Character.UnicodeBlock.HIRAGANA
                    || block == Character.UnicodeBlock.KATAKANA
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                return true;
            }
        }
        return false;
    }
}
