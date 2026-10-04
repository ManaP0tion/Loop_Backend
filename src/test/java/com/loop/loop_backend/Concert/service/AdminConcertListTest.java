package com.loop.loop_backend.Concert.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
import com.loop.loop_backend.Concert.domain.ticket.ConcertGeneralSale;
import com.loop.loop_backend.Concert.domain.ticket.ConcertPresale;
import com.loop.loop_backend.Concert.dto.admin.AdminConcertRow;
import com.loop.loop_backend.Concert.repository.AdminConcertRepository;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Concert.repository.ticket.ConcertGeneralSaleRepository;
import com.loop.loop_backend.Concert.repository.ticket.ConcertPresaleRepository;
import com.loop.loop_backend.Venue.domain.Venue;
import com.loop.loop_backend.Venue.repository.VenueRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 공연 관리 > 등록된 공연 목록 요구사항:
// - 공개·비공개 공연을 모두 보여주고 최근 등록순이다. 유형·공개 여부로 거를 수 있다.
// - 검색어는 공연명, 공연명 별칭, 아티스트 이름(별칭 포함), 장소(공연장 이름, 공연장 미연결 공연은 KOPIS 장소)에서 부분 일치(대소문자 무시).
//   별칭이 여러 개 걸려도 같은 공연은 한 번만 나온다.
// - 한 행: 유형, 제목, 장소, 일자, 공개 여부, 예매 등록 여부, 수정 화면용 id.
// - 예매 등록 여부는 예매 일시가 입력된 선예매·일반예매가 하나라도 있을 때 true(일시 없는 블록만 있으면 false).
// - 공연 수가 늘어도 목록 조회 쿼리 수는 같다(N+1 없음).
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class AdminConcertListTest {

    @Autowired private ConcertRepository concertRepository;
    @Autowired private AdminConcertRepository adminConcertRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private ConcertPresaleRepository presaleRepository;
    @Autowired private ConcertGeneralSaleRepository generalSaleRepository;
    @Autowired private EntityManager em;

    private AdminConcertService service;

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"));
    private static final LocalDateTime OCT_20 = LocalDateTime.of(2026, 10, 20, 20, 0);

    @BeforeEach
    void setUp() {
        service = new AdminConcertService(concertRepository, adminConcertRepository, artistRepository, venueRepository,
                presaleRepository, generalSaleRepository);
    }

    private Concert concert(String title, ConcertCategory category, boolean published) {
        Concert concert = Concert.builder().title(title).category(category)
                .startDate(LocalDate.of(2026, 12, 5)).endDate(LocalDate.of(2026, 12, 6)).build();
        concert.changePublished(published, LocalDateTime.now());
        return concertRepository.save(concert);
    }

    private List<AdminConcertRow> search(String q, ConcertCategory category, Boolean published) {
        em.flush();
        em.clear();
        return service.search(q, category, published, FIRST_PAGE).getContent();
    }

    private static List<String> titles(List<AdminConcertRow> rows) {
        return rows.stream().map(AdminConcertRow::title).toList();
    }

    // ---------- 목록·필터 ----------

    @Test
    void 공개와_비공개_공연이_모두_최근_등록순으로_나온다() {
        concert("A", ConcertCategory.J_POP_ARTIST, true);
        concert("B", ConcertCategory.J_POP_ARTIST, false);
        concert("C", ConcertCategory.JAPAN_FESTIVAL, false);

        assertThat(titles(search(null, null, null))).containsExactly("C", "B", "A");
    }

    @Test
    void 유형과_공개_여부로_거른다() {
        concert("내한 공개", ConcertCategory.J_POP_ARTIST, true);
        concert("내한 비공개", ConcertCategory.J_POP_ARTIST, false);
        concert("페스티벌 비공개", ConcertCategory.JAPAN_FESTIVAL, false);

        assertThat(titles(search(null, ConcertCategory.JAPAN_FESTIVAL, null))).containsExactly("페스티벌 비공개");
        assertThat(titles(search(null, null, false))).containsExactlyInAnyOrder("내한 비공개", "페스티벌 비공개");
        assertThat(titles(search(null, ConcertCategory.J_POP_ARTIST, true))).containsExactly("내한 공개");
    }

    // ---------- 검색어 ----------

    @Test
    void 검색어는_공연명_별칭_아티스트_장소에서_대소문자_구분_없이_찾는다() {
        Concert byTitle = concert("YUURI LIVE [서울]", ConcertCategory.J_POP_ARTIST, true);
        Concert byAlias = concert("Official HIGE DANdism Arena Tour", ConcertCategory.J_POP_ARTIST, true);
        byAlias.replaceTitleAliases(List.of("히게단"));
        Concert byArtist = concert("ASIA TOUR 2026", ConcertCategory.J_POP_ARTIST, true);
        byArtist.changeArtist(artistRepository.save(Artist.builder().name("YOASOBI").nameKo("요아소비")
                .category(ConcertCategory.J_POP_ARTIST).build()));
        Concert byVenue = concert("SUMMER SONIC 2026", ConcertCategory.JAPAN_FESTIVAL, true);
        byVenue.changeVenue(venueRepository.save(Venue.builder().name("KSPO DOME").address("서울특별시 송파구").build()));
        concertRepository.save(Concert.builder().title("기존 공연").category(ConcertCategory.J_POP_ARTIST)
                .venue("인스파이어 엔터테인먼트 리조트").build()); // 공연장 미연결 - KOPIS 장소 문자열

        assertThat(titles(search("yuuri", null, null))).containsExactly("YUURI LIVE [서울]");
        assertThat(titles(search("히게단", null, null))).containsExactly("Official HIGE DANdism Arena Tour");
        assertThat(titles(search("요아소비", null, null))).containsExactly("ASIA TOUR 2026");
        assertThat(titles(search("kspo", null, null))).containsExactly("SUMMER SONIC 2026");
        assertThat(titles(search("인스파이어", null, null))).containsExactly("기존 공연");
    }

    @Test
    void 별칭이_여러_개_걸려도_같은_공연은_한_번만_나오고_전체_개수도_맞다() {
        Concert concert = concert("Official HIGE DANdism", ConcertCategory.J_POP_ARTIST, true);
        concert.replaceTitleAliases(List.of("히게단", "오피셜히게단디즘"));
        em.flush();
        em.clear();

        Page<AdminConcertRow> page = service.search("히게단", null, null, FIRST_PAGE);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    // ---------- 행 ----------

    @Test
    void 한_행에는_id_유형_제목_장소_일자_공개_여부가_있다() {
        Concert concert = concert("YUURI LIVE [서울]", ConcertCategory.J_POP_ARTIST, false);
        concert.changeVenue(venueRepository.save(Venue.builder().name("인스파이어 아레나").address("인천광역시 중구").build()));

        AdminConcertRow row = search(null, null, null).get(0);

        assertThat(row.id()).isEqualTo(concert.getId());
        assertThat(row.category()).isEqualTo(ConcertCategory.J_POP_ARTIST);
        assertThat(row.title()).isEqualTo("YUURI LIVE [서울]");
        assertThat(row.venueName()).isEqualTo("인스파이어 아레나");
        assertThat(row.startDate()).isEqualTo(LocalDate.of(2026, 12, 5));
        assertThat(row.endDate()).isEqualTo(LocalDate.of(2026, 12, 6));
        assertThat(row.published()).isFalse();
    }

    @Test
    void 공연장이_연결되지_않은_공연은_장소가_비어_있다() {
        concert("YUURI LIVE [서울]", ConcertCategory.J_POP_ARTIST, true);

        assertThat(search(null, null, null).get(0).venueName()).isNull();
    }

    // ---------- 예매 등록 여부 ----------

    @Test
    void 예매_일시가_입력된_선예매나_일반예매가_있어야_예매_등록으로_본다() {
        Concert none = concert("예매 없음", ConcertCategory.J_POP_ARTIST, true);
        Concert undated = concert("일시 없는 일반예매만", ConcertCategory.J_POP_ARTIST, true);
        generalSaleRepository.save(ConcertGeneralSale.builder().concert(undated)
                .vendors(List.of(new TicketVendorInfo("NOL", "https://nol"))).build());
        Concert presaleDated = concert("선예매 일시 있음", ConcertCategory.J_POP_ARTIST, true);
        presaleRepository.save(ConcertPresale.builder().concert(presaleDated).opensAt(OCT_20).build());
        Concert generalDated = concert("일반예매 일시 있음", ConcertCategory.J_POP_ARTIST, true);
        generalSaleRepository.save(ConcertGeneralSale.builder().concert(generalDated).opensAt(OCT_20).build());

        List<AdminConcertRow> rows = search(null, null, null);

        assertThat(rows).filteredOn(AdminConcertRow::ticketScheduled).extracting(AdminConcertRow::title)
                .containsExactlyInAnyOrder("선예매 일시 있음", "일반예매 일시 있음");
    }

    // ---------- 쿼리 수 ----------

    private long queryCount(Runnable action) {
        em.flush();
        em.clear();
        Statistics stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        action.run();
        return stats.getPrepareStatementCount();
    }

    private void concertWithVenueArtistAndSale(int i) {
        Concert concert = concert("공연 " + i, ConcertCategory.J_POP_ARTIST, true);
        concert.changeVenue(venueRepository.save(Venue.builder().name("공연장 " + i).address("서울특별시 송파구").build()));
        concert.changeArtist(artistRepository.save(Artist.builder().name("아티스트 " + i)
                .category(ConcertCategory.J_POP_ARTIST).build()));
        generalSaleRepository.save(ConcertGeneralSale.builder().concert(concert).opensAt(OCT_20).build());
    }

    @Test
    void 공연이_늘어도_목록_조회_쿼리_수는_같다() {
        concertWithVenueArtistAndSale(1);
        long withOne = queryCount(() -> service.search(null, null, null, FIRST_PAGE));

        for (int i = 2; i <= 5; i++) concertWithVenueArtistAndSale(i);
        long withFive = queryCount(() -> service.search(null, null, null, FIRST_PAGE));

        assertThat(withOne).isPositive(); // 통계가 꺼져 있으면 둘 다 0이라 비교가 의미 없다
        assertThat(withFive).isEqualTo(withOne);
    }
}