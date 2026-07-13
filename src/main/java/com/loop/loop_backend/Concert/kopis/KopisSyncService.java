package com.loop.loop_backend.Concert.kopis;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KopisSyncService {

    private final ArtistRepository artistRepository;
    private final ConcertRepository concertRepository;
    private final KopisClient kopisClient;

    public void syncAll() {
        List<Artist> artists = artistRepository.findByAutoFetchConcertsTrue();
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
        // 1단계: 타이틀에서 아티스트 매칭 (API 추가 호출 없음)
        List<Artist> matched = artists.stream()
                .filter(a -> nameContainedInTitle(perf.getTitle(), a))
                .collect(Collectors.toList());

        if (matched.isEmpty()) {
            // 2단계: 타이틀 미매칭 → 출연진(prfcast) 정확 일치 확인
            String cast = kopisClient.getPerformanceCast(perf.getKopisId());
            if (cast == null) return;
            matched = artists.stream()
                    .filter(a -> castExactlyMatchesOfficialName(cast, a))
                    .collect(Collectors.toList());
        }

        for (Artist artist : matched) {
            upsert(artist, perf);
        }
    }

    private void upsert(Artist artist, KopisPerformance perf) {
        concertRepository.findByKopisIdAndArtistId(perf.getKopisId(), artist.getId())
                .ifPresentOrElse(
                        existing -> existing.updateFromKopis(
                                perf.getTitle(), perf.getPosterUrl(), perf.getVenue(),
                                perf.getStartDate(), perf.getEndDate()),
                        () -> concertRepository.save(Concert.builder()
                                .artist(artist)
                                .kopisId(perf.getKopisId())
                                .title(perf.getTitle())
                                .posterUrl(perf.getPosterUrl())
                                .venue(perf.getVenue())
                                .startDate(perf.getStartDate())
                                .endDate(perf.getEndDate())
                                .build())
                );
    }

    /**
     * 타이틀에 아티스트 name / nameKo가 단어 단위로 포함되면 true.
     * alias는 사용하지 않음 — "미세스" 같이 일반 단어와 겹치는 alias가 오탐을 유발하기 때문.
     */
    private boolean nameContainedInTitle(String title, Artist artist) {
        if (title == null) return false;

        if (wordBoundaryMatch(title, artist.getName())) return true;

        if (artist.getNameKo() != null) {
            return Arrays.stream(artist.getNameKo().split(" / "))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .anyMatch(ko -> wordBoundaryMatch(title, ko));
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
     * 출연진(prfcast)을 쉼표로 분리해 공식 영문명(name)과 정확히 일치하는 항목이 있으면 true.
     * 예) cast = "ZUTOMAYO, Eve, LiSA" → artist.getName() = "Eve" → true
     */
    private boolean castExactlyMatchesOfficialName(String cast, Artist artist) {
        if (cast == null || artist.getName() == null) return false;
        String officialName = artist.getName().toLowerCase();
        return Arrays.stream(cast.split(","))
                .map(String::trim)
                .anyMatch(token -> token.toLowerCase().equals(officialName));
    }
}
