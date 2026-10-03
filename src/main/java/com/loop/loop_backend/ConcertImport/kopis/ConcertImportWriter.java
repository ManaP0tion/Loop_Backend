package com.loop.loop_backend.ConcertImport.kopis;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.ConcertImport.domain.ConcertImport;
import com.loop.loop_backend.ConcertImport.domain.ImportStatus;
import com.loop.loop_backend.ConcertImport.repository.ConcertImportRepository;
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

    /** 공연 1건의 식별 결과를 저장하고, 행 단위로 새로 저장/갱신/건너뜀을 센 값을 돌려준다. */
    @Transactional
    public ImportSaveResult saveImport(KopisPerformance perf, IdentificationResult result) {
        // 페스티벌은 아티스트 없이 (kopisId) 단일 건, 아티스트 공연은 (kopisId, 아티스트)마다 한 건
        // 같은 공연의 분류가 바뀌면(예: 아티스트 공연 → 페스티벌) 키가 달라 새 행이 생기고, 이전 분류의 행은 정리하지 않는다.
        if (result.artists().isEmpty()) {
            return upsertImport(null, perf, result,
                    concertImportRepository.findByKopisIdAndMatchedArtistIsNull(perf.getKopisId()));
        }
        ImportSaveResult total = ImportSaveResult.NONE;
        for (Artist artist : result.artists()) {
            total = total.plus(upsertImport(artist, perf, result,
                    concertImportRepository.findByKopisIdAndMatchedArtist_Id(perf.getKopisId(), artist.getId())));
        }
        return total;
    }

    /**
     * 재수집 멱등성:
     * - 신규 → PENDING import 저장
     * - 기존 PENDING → 원본 필드만 최신화 (아직 검토 전)
     * - 기존 APPROVED/REJECTED → skip (이미 처리한 건을 검토 큐에 되살리지 않음)
     */
    private ImportSaveResult upsertImport(Artist artist, KopisPerformance perf, IdentificationResult result,
                                          Optional<ConcertImport> existing) {
        if (existing.isEmpty()) {
            concertImportRepository.save(ConcertImport.builder()
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
                    .build());
            return ImportSaveResult.CREATED;
        }
        ConcertImport imp = existing.get();
        if (imp.getStatus() != ImportStatus.PENDING) {
            return ImportSaveResult.SKIPPED;
        }
        boolean changed = imp.updateFromKopis(perf.getTitle(), perf.getPosterUrl(), perf.getVenue(),
                perf.getStartDate(), perf.getEndDate(), result.category(), result.matchReason());
        return changed ? ImportSaveResult.UPDATED : ImportSaveResult.UNCHANGED;
    }
}