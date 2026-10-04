package com.loop.loop_backend.Concert.domain;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.ticket.ConcertGeneralSale;
import com.loop.loop_backend.Concert.domain.ticket.ConcertPresale;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Concert.repository.ticket.ConcertGeneralSaleRepository;
import com.loop.loop_backend.Concert.repository.ticket.ConcertPresaleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 공연 관리자 입력 항목(AD-01) 저장 요구사항:
// - 새로 만드는 공연은 비공개, 숙소 노출 Off로 시작한다. 이 항목들이 생기기 전부터 있던 공연은 공개로 남는다.
// - 공연명 별칭·관련 상품 코드는 여러 개, DAY별 공연 시각은 날짜마다 저장하고 시각은 비워 둘 수 있다.
// - 선예매·일반예매는 각각 여러 건 등록할 수 있고, 한 건 = 예매 일시 1개 + 예매처(이름 + URL) 여러 개. 예매 일시는 비워도 저장된다.
// - 공연이 지워지면(공연 삭제, 아티스트 삭제 연쇄) 이 항목들도 함께 지워진다.
@DataJpaTest
class ConcertAdminFieldsPersistenceTest {

    @Autowired private ConcertRepository concertRepository;
    @Autowired private ConcertPresaleRepository presaleRepository;
    @Autowired private ConcertGeneralSaleRepository generalSaleRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private EntityManager em;

    private Concert saveConcert(Artist artist) {
        return concertRepository.save(Concert.builder()
                .artist(artist)
                .title("YUURI LIVE [서울]")
                .category(ConcertCategory.J_POP_ARTIST)
                .build());
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private long countRows(String table, Long concertId) {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM " + table + " WHERE concert_id = :id")
                .setParameter("id", concertId).getSingleResult()).longValue();
    }

    // ---------- 기본값 ----------

    @Test
    void 새로_만드는_공연은_비공개이고_숙소_노출은_꺼져_있다() {
        Concert saved = saveConcert(null);
        flushAndClear();

        Concert found = concertRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.isPublished()).isFalse();
        assertThat(found.isLodgingVisible()).isFalse();
        assertThat(found.getTitleAliases()).isEmpty();
        assertThat(found.getProductCodes()).isEmpty();
        assertThat(found.getShowtimes()).isEmpty();
    }

    @Test
    void 공개_여부_항목이_생기기_전부터_있던_공연은_공개로_남는다() {
        // 컬럼이 추가될 때 기존 행은 컬럼 기본값으로 채워진다 - 공개 여부를 지정하지 않은 행이 공개인지 본다
        em.createNativeQuery("INSERT INTO concerts (title, category) VALUES ('기존 공연', 'J_POP_ARTIST')").executeUpdate();

        Object published = em.createNativeQuery("SELECT published FROM concerts WHERE title = '기존 공연'").getSingleResult();
        Object lodgingVisible = em.createNativeQuery("SELECT lodging_visible FROM concerts WHERE title = '기존 공연'").getSingleResult();

        assertThat(published).isEqualTo(true);
        assertThat(lodgingVisible).isEqualTo(false);
    }

    // ---------- 여러 개 값 ----------

    @Test
    void 별칭과_상품_코드는_여러_개_저장된다() {
        Concert concert = concertRepository.save(Concert.builder()
                .title("Official髭男dism Arena Tour")
                .category(ConcertCategory.J_POP_ARTIST)
                .titleAliases(List.of("히게단", "오피셜히게단디즘"))
                .productCodes(List.of("PCXP-51237", "PCXP-51238"))
                .build());
        flushAndClear();

        Concert found = concertRepository.findById(concert.getId()).orElseThrow();
        assertThat(found.getTitleAliases()).containsExactlyInAnyOrder("히게단", "오피셜히게단디즘");
        assertThat(found.getProductCodes()).containsExactlyInAnyOrder("PCXP-51237", "PCXP-51238");
    }

    @Test
    void DAY별_공연_시각은_날짜_순으로_나오고_시각은_비워_둘_수_있다() {
        Concert concert = concertRepository.save(Concert.builder()
                .title("YOASOBI ASIA TOUR")
                .category(ConcertCategory.J_POP_ARTIST)
                .startDate(LocalDate.of(2026, 12, 5))
                .endDate(LocalDate.of(2026, 12, 6))
                .showtimes(List.of(
                        new ConcertShowtime(LocalDate.of(2026, 12, 6), null),               // 시각 미정
                        new ConcertShowtime(LocalDate.of(2026, 12, 5), LocalTime.of(18, 0))))
                .build());
        flushAndClear();

        List<ConcertShowtime> showtimes = concertRepository.findById(concert.getId()).orElseThrow().getShowtimes();
        assertThat(showtimes).extracting(ConcertShowtime::getDate)
                .containsExactly(LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 6));
        assertThat(showtimes.get(0).getStartTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(showtimes.get(1).getStartTime()).isNull();
    }

    // ---------- 선예매·일반예매 ----------

    @Test
    void 선예매와_일반예매는_각각_여러_건_등록되고_한_건에_예매처가_여러_개다() {
        Concert concert = saveConcert(null);
        presaleRepository.save(ConcertPresale.builder().concert(concert)
                .opensAt(LocalDateTime.of(2026, 10, 20, 20, 0))
                .vendors(List.of(new TicketVendorInfo("공식 안내", "https://notice"),
                        new TicketVendorInfo("팬클럽", "https://fc")))
                .build());
        presaleRepository.save(ConcertPresale.builder().concert(concert)
                .vendors(List.of(new TicketVendorInfo("카드사 선예매", "https://card")))
                .build());                                                                // 예매 일시 미정
        generalSaleRepository.save(ConcertGeneralSale.builder().concert(concert)
                .opensAt(LocalDateTime.of(2026, 10, 25, 20, 0))
                .vendors(List.of(new TicketVendorInfo("NOL", "https://nol"),
                        new TicketVendorInfo("티켓링크", "https://link")))
                .build());
        flushAndClear();

        List<ConcertPresale> presales = presaleRepository.findByConcert_Id(concert.getId());
        assertThat(presales).hasSize(2);
        assertThat(presales).filteredOn(p -> p.getOpensAt() == null).singleElement()
                .satisfies(p -> assertThat(p.getVendors()).extracting(TicketVendorInfo::name).containsExactly("카드사 선예매"));
        assertThat(presales).filteredOn(p -> p.getOpensAt() != null).singleElement()
                .satisfies(p -> assertThat(p.getVendors()).containsExactly(
                        new TicketVendorInfo("공식 안내", "https://notice"), new TicketVendorInfo("팬클럽", "https://fc")));

        assertThat(generalSaleRepository.findByConcert_Id(concert.getId())).singleElement()
                .satisfies(s -> assertThat(s.getVendors()).containsExactly(
                        new TicketVendorInfo("NOL", "https://nol"), new TicketVendorInfo("티켓링크", "https://link")));
    }

    @Test
    void 다른_공연의_예매_건은_그_공연_경로로_찾을_수_없다() {
        Concert mine = saveConcert(null);
        Concert other = saveConcert(null);
        ConcertPresale othersPresale = presaleRepository.save(ConcertPresale.builder().concert(other).build());
        ConcertGeneralSale othersSale = generalSaleRepository.save(ConcertGeneralSale.builder().concert(other).build());

        assertThat(presaleRepository.findByIdAndConcert_Id(othersPresale.getId(), mine.getId())).isEmpty();
        assertThat(generalSaleRepository.findByIdAndConcert_Id(othersSale.getId(), mine.getId())).isEmpty();
    }

    // ---------- 함께 삭제 ----------

    private Concert concertWithEverything(Artist artist) {
        Concert concert = concertRepository.save(Concert.builder()
                .artist(artist)
                .title("YUURI LIVE [서울]")
                .category(ConcertCategory.J_POP_ARTIST)
                .titleAliases(List.of("유우리"))
                .productCodes(List.of("PCXP-1"))
                .showtimes(List.of(new ConcertShowtime(LocalDate.of(2026, 12, 5), LocalTime.of(18, 0))))
                .build());
        presaleRepository.save(ConcertPresale.builder().concert(concert).build());
        generalSaleRepository.save(ConcertGeneralSale.builder().concert(concert).build());
        flushAndClear();
        return concert;
    }

    private void assertNoChildRows(Long concertId) {
        assertThat(countRows("concert_title_aliases", concertId)).isZero();
        assertThat(countRows("concert_product_codes", concertId)).isZero();
        assertThat(countRows("concert_showtimes", concertId)).isZero();
        assertThat(countRows("concert_presales", concertId)).isZero();
        assertThat(countRows("concert_general_sales", concertId)).isZero();
    }

    @Test
    void 공연을_삭제하면_별칭_상품_코드_공연_시각_예매_정보가_함께_지워진다() {
        Concert concert = concertWithEverything(null);

        concertRepository.deleteById(concert.getId());
        flushAndClear();

        assertNoChildRows(concert.getId());
    }

    @Test
    void 아티스트가_지워져_공연이_함께_지워질_때도_하위_정보가_남지_않는다() {
        Artist artist = artistRepository.save(Artist.builder().name("Yuuri")
                .autoFetchConcerts(true).category(ConcertCategory.J_POP_ARTIST).build());
        Concert concert = concertWithEverything(artist);

        // 공연은 아티스트 삭제 시 DB가 직접 지운다(ON DELETE CASCADE) - JPA를 거치지 않는 경로를 그대로 재현
        em.createNativeQuery("DELETE FROM artists WHERE id = :id").setParameter("id", artist.getId()).executeUpdate();
        flushAndClear();

        assertThat(concertRepository.findById(concert.getId())).isEmpty();
        assertNoChildRows(concert.getId());
    }
}