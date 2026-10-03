package com.loop.loop_backend.ConcertImport.kopis;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.ConcertImport.domain.ConcertImport;
import com.loop.loop_backend.ConcertImport.domain.ImportStatus;
import com.loop.loop_backend.ConcertImport.repository.ConcertImportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 검토 큐 저장 결과 집계 요구사항: 싱크 완료 로그에서 새로 들어온 건지, 기존 행 갱신인지 구분할 수 있어야 한다.
// - 검토 큐에 없던 공연이면 새로 저장한 것으로 센다 (아티스트 공연은 아티스트마다 1건).
// - 검토 전(PENDING) 행이 있으면 최신 정보로 갱신한 것으로 센다.
// - 이미 승인/반려된 행이면 건드리지 않고 건너뛴 것으로 센다.
@DataJpaTest
class ConcertImportWriterTest {

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private ConcertImportRepository concertImportRepository;

    private ConcertImportWriter writer;

    @BeforeEach
    void setUp() {
        writer = new ConcertImportWriter(concertImportRepository);
    }

    private Artist jpopArtist(String name) {
        return artistRepository.save(Artist.builder()
                .name(name)
                .autoFetchConcerts(true)
                .category(ConcertCategory.J_POP_ARTIST)
                .build());
    }

    private static KopisPerformance perf(String kopisId, String title) {
        return KopisPerformance.builder()
                .kopisId(kopisId)
                .title(title)
                .venue("인스파이어 아레나")
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 2))
                .build();
    }

    private void saveExisting(String kopisId, Artist artist, ImportStatus status) {
        concertImportRepository.save(ConcertImport.builder()
                .kopisId(kopisId)
                .matchedArtist(artist)
                .title("이전 제목")
                .suggestedCategory(ConcertCategory.J_POP_ARTIST)
                .matchReason(IdentificationResult.TITLE_MATCH)
                .status(status)
                .build());
    }

    @Test
    void 검토_큐에_없던_공연이면_새로_저장한_1건으로_센다() {
        Artist vaundy = jpopArtist("Vaundy");

        ImportSaveResult result = writer.saveImport(perf("PF1", "Vaundy LIVE"),
                IdentificationResult.jpopArtists(List.of(vaundy), IdentificationResult.TITLE_MATCH));

        assertThat(result).isEqualTo(new ImportSaveResult(1, 0, 0));
    }

    @Test
    void 아티스트가_여러_명인_공연은_아티스트마다_새로_저장한_건으로_센다() {
        Artist yoasobi = jpopArtist("YOASOBI");
        Artist ado = jpopArtist("Ado");

        ImportSaveResult result = writer.saveImport(perf("PF1", "YOASOBI × Ado SPECIAL LIVE"),
                IdentificationResult.jpopArtists(List.of(yoasobi, ado), IdentificationResult.TITLE_MATCH));

        assertThat(result).isEqualTo(new ImportSaveResult(2, 0, 0));
    }

    @Test
    void 페스티벌은_아티스트_없이_1건으로_센다() {
        ImportSaveResult result = writer.saveImport(perf("PF1", "SUMMER SONIC 2026"),
                IdentificationResult.japanFestival());

        assertThat(result).isEqualTo(new ImportSaveResult(1, 0, 0));
    }

    @Test
    void 검토_전인_행이_있으면_갱신한_것으로_센다() {
        Artist vaundy = jpopArtist("Vaundy");
        saveExisting("PF1", vaundy, ImportStatus.PENDING);

        ImportSaveResult result = writer.saveImport(perf("PF1", "Vaundy LIVE 2026"),
                IdentificationResult.jpopArtists(List.of(vaundy), IdentificationResult.TITLE_MATCH));

        assertThat(result).isEqualTo(new ImportSaveResult(0, 1, 0));
        assertThat(concertImportRepository.findAll()).singleElement()
                .satisfies(imp -> assertThat(imp.getTitle()).isEqualTo("Vaundy LIVE 2026"));
    }

    @Test
    void 이미_승인되거나_반려된_행은_건드리지_않고_건너뛴_것으로_센다() {
        Artist approved = jpopArtist("Vaundy");
        Artist rejected = jpopArtist("Ado");
        saveExisting("PF1", approved, ImportStatus.APPROVED);
        saveExisting("PF1", rejected, ImportStatus.REJECTED);

        ImportSaveResult result = writer.saveImport(perf("PF1", "새 제목"),
                IdentificationResult.jpopArtists(List.of(approved, rejected), IdentificationResult.TITLE_MATCH));

        assertThat(result).isEqualTo(new ImportSaveResult(0, 0, 2));
        assertThat(concertImportRepository.findAll())
                .allSatisfy(imp -> assertThat(imp.getTitle()).isEqualTo("이전 제목"));
    }

    @Test
    void 한_공연에서_새_행과_기존_행이_섞이면_각각_센다() {
        Artist pending = jpopArtist("Vaundy");
        Artist rejected = jpopArtist("Ado");
        Artist fresh = jpopArtist("YOASOBI");
        saveExisting("PF1", pending, ImportStatus.PENDING);
        saveExisting("PF1", rejected, ImportStatus.REJECTED);

        ImportSaveResult result = writer.saveImport(perf("PF1", "Vaundy × Ado × YOASOBI"),
                IdentificationResult.jpopArtists(List.of(pending, rejected, fresh), IdentificationResult.TITLE_MATCH));

        assertThat(result).isEqualTo(new ImportSaveResult(1, 1, 1));
    }
}