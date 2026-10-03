package com.loop.loop_backend.Concert.kopis;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Concert.domain.ConcertImport;
import com.loop.loop_backend.Concert.domain.ImportStatus;
import com.loop.loop_backend.Concert.repository.ConcertImportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 식별 결과를 검토 대기(ConcertImport)로 저장한다.
 * KopisSyncService와 다른 빈이라 @Transactional이 실제로 적용된다 - 공연 1건에서 생기는 행들이 함께 커밋/롤백된다.
 */
@Component
@RequiredArgsConstructor
public class ConcertImportWriter {

    private final ConcertImportRepository concertImportRepository;

    @Transactional
    public void saveImport(KopisPerformance perf, IdentificationResult result) {
        // 페스티벌은 아티스트 없이 (kopisId) 단일 건, 아티스트 공연은 (kopisId, 아티스트)마다 한 건
        if (result.artists().isEmpty()) {
            upsertImport(null, perf, result,
                    concertImportRepository.findByKopisIdAndMatchedArtistIsNull(perf.getKopisId()));
            return;
        }
        for (Artist artist : result.artists()) {
            upsertImport(artist, perf, result,
                    concertImportRepository.findByKopisIdAndMatchedArtist_Id(perf.getKopisId(), artist.getId()));
        }
    }

    /**
     * 재수집 멱등성:
     * - 신규 → PENDING import 저장
     * - 기존 PENDING → 원본 필드만 최신화 (아직 검토 전)
     * - 기존 APPROVED/REJECTED → skip (이미 처리한 건을 검토 큐에 되살리지 않음)
     */
    private void upsertImport(Artist artist, KopisPerformance perf, IdentificationResult result,
                              Optional<ConcertImport> existing) {
        existing.ifPresentOrElse(
                imp -> {
                    if (imp.getStatus() == ImportStatus.PENDING) {
                        imp.updateFromKopis(perf.getTitle(), perf.getPosterUrl(), perf.getVenue(),
                                perf.getStartDate(), perf.getEndDate(), result.category(), result.matchReason());
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
                        .suggestedCategory(result.category())
                        .matchReason(result.matchReason())
                        .status(ImportStatus.PENDING)
                        .build())
        );
    }
}