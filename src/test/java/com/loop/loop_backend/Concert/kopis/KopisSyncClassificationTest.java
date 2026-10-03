package com.loop.loop_backend.Concert.kopis;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.domain.ConcertImport;
import com.loop.loop_backend.Concert.domain.ImportStatus;
import com.loop.loop_backend.Concert.repository.ConcertImportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// KOPIS 수집 분류 요구사항: 수집한 공연이 검토 큐(concert_imports)에 어떻게 들어가는지를 검증한다.
// - 등록 아티스트(자동 수집 켜짐)의 이름이 제목에 단어 단위로 있으면 그 아티스트의 내한 공연으로 아티스트마다 1건.
// - 일본 페스티벌(화이트리스트 이름, 또는 "[일본" + 페스티벌 표시어)은 아티스트 없이 1건.
// - 제목으로 못 찾았을 때는 일본 신호가 있는 공연만 상세 API로 출연진을 확인한다 - 신호가 없으면 호출하지 않는다.
// - 재수집 시 검토 전(PENDING) 건은 최신값으로 갱신, 이미 처리된 건은 건드리지 않는다.
// - V2는 J-POP 공연만 수집한다 - 국내 아티스트는 자동 수집이 켜져 있어도 매칭 대상이 아니다.
//
// KOPIS 클라이언트만 가짜로 두고 리포지토리는 실제 DB를 쓴다 - 저장을 누가 하든 "검토 큐에 남은 결과"만 본다.
// 페스티벌 제목 + J-POP 아티스트 공연은 분류 요구사항이 바뀔 예정이라 여기서 다루지 않는다.
@DataJpaTest
class KopisSyncClassificationTest {

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private ConcertImportRepository concertImportRepository;

    private KopisClient kopisClient;
    private KopisSyncService kopisSyncService;

    @BeforeEach
    void setUp() {
        kopisClient = mock(KopisClient.class);
        kopisSyncService = new KopisSyncService(artistRepository, concertImportRepository, kopisClient);
    }

    private Artist jpopArtist(String name, String nameKo, String nameAlias) {
        return saveArtist(name, nameKo, nameAlias, true);
    }

    private Artist domesticArtist(String name, String nameKo) {
        return artistRepository.save(Artist.builder()
                .name(name)
                .nameKo(nameKo)
                .autoFetchConcerts(true)
                .category(ConcertCategory.DOMESTIC_ARTIST)
                .build());
    }

    private Artist saveArtist(String name, String nameKo, String nameAlias, boolean autoFetchConcerts) {
        return artistRepository.save(Artist.builder()
                .name(name)
                .nameKo(nameKo)
                .nameAlias(nameAlias)
                .autoFetchConcerts(autoFetchConcerts)
                .category(ConcertCategory.J_POP_ARTIST)
                .build());
    }

    private static KopisPerformance perf(String kopisId, String title) {
        return perf(kopisId, title, "인스파이어 아레나");
    }

    private static KopisPerformance perf(String kopisId, String title, String venue) {
        return KopisPerformance.builder()
                .kopisId(kopisId)
                .title(title)
                .venue(venue)
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 2))
                .build();
    }

    private void syncWith(KopisPerformance... performances) {
        when(kopisClient.getAllUpcomingPerformances()).thenReturn(List.of(performances));
        kopisSyncService.syncAll();
    }

    private List<ConcertImport> imports() {
        return concertImportRepository.findAll();
    }

    // ---------- 등록 아티스트 내한 공연 ----------

    @Test
    void 제목에_등록_아티스트_이름이_있으면_그_아티스트의_내한_공연_1건으로_검토_큐에_들어간다() {
        Artist vaundy = jpopArtist("Vaundy", "바운디", null);

        syncWith(perf("PF1", "Vaundy ASIA ARENA TOUR HORO IN SEOUL"));

        assertThat(imports()).singleElement().satisfies(imp -> {
            assertThat(imp.getKopisId()).isEqualTo("PF1");
            assertThat(imp.getMatchedArtist().getId()).isEqualTo(vaundy.getId());
            assertThat(imp.getSuggestedCategory()).isEqualTo(ConcertCategory.J_POP_ARTIST);
            assertThat(imp.getMatchReason()).isEqualTo("TITLE_MATCH");
            assertThat(imp.getStatus()).isEqualTo(ImportStatus.PENDING);
        });
    }

    @Test
    void 제목으로_아티스트를_찾으면_상세_API를_호출하지_않는다() {
        jpopArtist("Vaundy", null, null);

        syncWith(perf("PF1", "Vaundy ASIA ARENA TOUR HORO IN SEOUL"));

        verify(kopisClient, never()).getPerformanceCast(any());
    }

    @Test
    void 한글_표기로도_제목에서_아티스트를_찾는다() {
        jpopArtist("YOASOBI", "요아소비", null);

        syncWith(perf("PF1", "요아소비 내한공연"));

        assertThat(imports()).singleElement()
                .satisfies(imp -> assertThat(imp.getMatchedArtist().getName()).isEqualTo("YOASOBI"));
    }

    @Test
    void 한_공연에_등록_아티스트가_여러_명이면_아티스트마다_1건씩_들어간다() {
        jpopArtist("YOASOBI", null, null);
        jpopArtist("Ado", null, null);

        syncWith(perf("PF1", "YOASOBI × Ado SPECIAL LIVE"));

        assertThat(imports()).hasSize(2)
                .allSatisfy(imp -> {
                    assertThat(imp.getKopisId()).isEqualTo("PF1");
                    assertThat(imp.getSuggestedCategory()).isEqualTo(ConcertCategory.J_POP_ARTIST);
                })
                .extracting(imp -> imp.getMatchedArtist().getName())
                .containsExactlyInAnyOrder("YOASOBI", "Ado");
    }

    @Test
    void 자동_수집이_꺼진_아티스트는_제목에_있어도_매칭하지_않는다() {
        saveArtist("Vaundy", null, null, false);

        syncWith(perf("PF1", "Vaundy ASIA ARENA TOUR HORO IN SEOUL"));

        assertThat(imports()).isEmpty();
    }

    @Test
    void 아티스트_이름이_다른_단어의_일부로만_있으면_매칭하지_않는다() {
        jpopArtist("이브", null, null);

        syncWith(perf("PF1", "봄 라이브 2026"));

        assertThat(imports()).isEmpty();
    }

    @Test
    void 세_글자_이하_별칭만_제목에_있으면_매칭하지_않는다() {
        jpopArtist("Mrs. GREEN APPLE", null, "미세스");

        syncWith(perf("PF1", "미세스 콘서트"));

        assertThat(imports()).isEmpty();
    }

    // ---------- 일본 페스티벌 ----------

    @Test
    void 일본_페스티벌_이름이_제목에_있으면_아티스트_없이_일본_페스티벌_1건으로_들어간다() {
        syncWith(perf("PF1", "SUMMER SONIC 2026"));

        assertThat(imports()).singleElement().satisfies(imp -> {
            assertThat(imp.getMatchedArtist()).isNull();
            assertThat(imp.getSuggestedCategory()).isEqualTo(ConcertCategory.JAPAN_FESTIVAL);
            assertThat(imp.getMatchReason()).isEqualTo("JAPAN_FESTIVAL");
        });
    }

    @Test
    void 일본_표시와_페스티벌_표시가_함께_있으면_일본_페스티벌_1건으로_들어간다() {
        syncWith(perf("PF1", "[일본] 록 페스티벌 2026"));

        assertThat(imports()).singleElement().satisfies(imp -> {
            assertThat(imp.getMatchedArtist()).isNull();
            assertThat(imp.getSuggestedCategory()).isEqualTo(ConcertCategory.JAPAN_FESTIVAL);
        });
    }

    // ---------- 출연진으로 식별 ----------

    @Test
    void 제목으로_못_찾았지만_일본_신호가_있으면_출연진으로_아티스트를_찾는다() {
        jpopArtist("YOASOBI", "요아소비", null);
        when(kopisClient.getPerformanceCast("PF1")).thenReturn("요아소비, 아이유");

        syncWith(perf("PF1", "2026 JAPAN TOUR in SEOUL"));

        assertThat(imports()).singleElement().satisfies(imp -> {
            assertThat(imp.getMatchedArtist().getName()).isEqualTo("YOASOBI");
            assertThat(imp.getSuggestedCategory()).isEqualTo(ConcertCategory.J_POP_ARTIST);
            assertThat(imp.getMatchReason()).isEqualTo("CAST_MATCH");
        });
    }

    @Test
    void 출연진이_비어_있으면_검토_큐에_들어가지_않는다() {
        jpopArtist("YOASOBI", "요아소비", null);
        when(kopisClient.getPerformanceCast("PF1")).thenReturn(null);

        syncWith(perf("PF1", "2026 JAPAN TOUR in SEOUL"));

        assertThat(imports()).isEmpty();
    }

    @Test
    void 출연진에_등록_아티스트가_없으면_검토_큐에_들어가지_않는다() {
        jpopArtist("YOASOBI", "요아소비", null);
        when(kopisClient.getPerformanceCast("PF1")).thenReturn("아이유, 성시경");

        syncWith(perf("PF1", "2026 JAPAN TOUR in SEOUL"));

        assertThat(imports()).isEmpty();
    }

    @Test
    void 제목으로_못_찾고_일본_신호도_없으면_상세_API를_호출하지_않고_버린다() {
        jpopArtist("YOASOBI", "요아소비", null);

        syncWith(perf("PF1", "봄 콘서트 2026"));

        verify(kopisClient, never()).getPerformanceCast(any());
        assertThat(imports()).isEmpty();
    }

    // ---------- 국내 공연 (V2 수집 대상 아님) ----------

    @Test
    void 국내_아티스트는_자동_수집이_켜져_있어도_제목에_있는_공연을_수집하지_않는다() {
        domesticArtist("Nell", "넬");

        syncWith(perf("PF1", "Nell 단독 콘서트"));

        assertThat(imports()).isEmpty();
    }

    @Test
    void 페스티벌_제목에_국내_아티스트만_있으면_수집하지_않는다() {
        domesticArtist("Nell", "넬");

        syncWith(perf("PF1", "2026 서울 FESTIVAL - Nell"));

        assertThat(imports()).isEmpty();
    }

    @Test
    void 출연진에_국내_아티스트만_있으면_수집하지_않는다() {
        domesticArtist("Nell", "넬");
        when(kopisClient.getPerformanceCast("PF1")).thenReturn("넬");

        syncWith(perf("PF1", "2026 ASIA TOUR in SEOUL"));

        assertThat(imports()).isEmpty();
    }

    @Test
    void 한_공연에_J_POP과_국내_아티스트가_함께_있으면_J_POP_아티스트_공연만_수집한다() {
        jpopArtist("YOASOBI", null, null);
        domesticArtist("Nell", "넬");

        syncWith(perf("PF1", "YOASOBI × Nell SPECIAL LIVE"));

        assertThat(imports()).singleElement().satisfies(imp -> {
            assertThat(imp.getMatchedArtist().getName()).isEqualTo("YOASOBI");
            assertThat(imp.getSuggestedCategory()).isEqualTo(ConcertCategory.J_POP_ARTIST);
        });
    }

    // ---------- 재수집 ----------

    @Test
    void 검토_전인_공연을_다시_수집하면_새로_쌓지_않고_최신_정보로_갱신한다() {
        jpopArtist("Vaundy", null, null);
        syncWith(perf("PF1", "Vaundy LIVE", "올림픽홀"));

        syncWith(perf("PF1", "Vaundy LIVE 2026", "인스파이어 아레나"));

        assertThat(imports()).singleElement().satisfies(imp -> {
            assertThat(imp.getTitle()).isEqualTo("Vaundy LIVE 2026");
            assertThat(imp.getVenue()).isEqualTo("인스파이어 아레나");
            assertThat(imp.getStatus()).isEqualTo(ImportStatus.PENDING);
        });
    }

    @Test
    void 반려된_공연은_다시_수집해도_되살아나거나_바뀌지_않는다() {
        Artist vaundy = jpopArtist("Vaundy", null, null);
        concertImportRepository.save(ConcertImport.builder()
                .kopisId("PF1")
                .matchedArtist(vaundy)
                .title("Vaundy LIVE")
                .suggestedCategory(ConcertCategory.J_POP_ARTIST)
                .matchReason("TITLE_MATCH")
                .status(ImportStatus.REJECTED)
                .build());

        syncWith(perf("PF1", "Vaundy LIVE 2026"));

        assertThat(imports()).singleElement().satisfies(imp -> {
            assertThat(imp.getStatus()).isEqualTo(ImportStatus.REJECTED);
            assertThat(imp.getTitle()).isEqualTo("Vaundy LIVE");
        });
    }
}