package com.loop.loop_backend.Concert.service;

import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
import com.loop.loop_backend.Concert.dto.admin.AdminConcertDetailResponse;
import com.loop.loop_backend.Concert.dto.admin.TicketSaleRequest;
import com.loop.loop_backend.Concert.dto.admin.TicketSaleRequest.Vendor;
import com.loop.loop_backend.Concert.dto.admin.TicketSaleResponse;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Concert.repository.ticket.ConcertGeneralSaleRepository;
import com.loop.loop_backend.Concert.repository.ticket.ConcertPresaleRepository;
import com.loop.loop_backend.Venue.repository.VenueRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 선예매·일반예매 요구사항(AD-01 예매 유형):
// - 선예매·일반예매는 각각 따로 추가·수정·삭제한다. 한 건 = 예매 일시 + 예매처(이름 + 링크) 여러 개.
// - 예매 일시는 비워도 저장된다. 예매처 이름은 필수, 링크는 선택(빈 값은 비움). 예매처 0개도 허용.
// - 수정은 블록 전체 교체(PUT): 예매 일시를 null로 보내면 지워지고, 예매처를 안 보내면 비워진다.
// - 다른 공연의 예매 건은 그 공연 경로로 수정·삭제할 수 없다(404).
// - 공연 상세에는 예매 일시 순으로 나오고, 일시가 없는 건은 뒤에 온다.
@DataJpaTest
class AdminTicketSaleServiceTest {

    @Autowired private ConcertRepository concertRepository;
    @Autowired private ConcertPresaleRepository presaleRepository;
    @Autowired private ConcertGeneralSaleRepository generalSaleRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private EntityManager em;

    private AdminTicketSaleService service;
    private AdminConcertService concertService;
    private Concert concert;

    private static final LocalDateTime OCT_20 = LocalDateTime.of(2026, 10, 20, 20, 0);
    private static final LocalDateTime OCT_25 = LocalDateTime.of(2026, 10, 25, 20, 0);

    @BeforeEach
    void setUp() {
        service = new AdminTicketSaleService(concertRepository, presaleRepository, generalSaleRepository);
        concertService = new AdminConcertService(concertRepository, artistRepository, venueRepository,
                presaleRepository, generalSaleRepository);
        concert = concertRepository.save(Concert.builder()
                .title("YUURI LIVE [서울]").category(ConcertCategory.J_POP_ARTIST).build());
    }

    private static TicketSaleRequest request(LocalDateTime opensAt, Vendor... vendors) {
        return new TicketSaleRequest(opensAt, List.of(vendors));
    }

    private static void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    // ---------- 추가 ----------

    @Test
    void 선예매와_일반예매를_각각_추가한다() {
        service.addPresale(concert.getId(), request(OCT_20, new Vendor("팬클럽 선예매", "https://fc")));
        service.addGeneralSale(concert.getId(), request(OCT_25, new Vendor("NOL", "https://nol"), new Vendor("티켓링크", "https://link")));
        flushAndClear();

        AdminConcertDetailResponse detail = concertService.get(concert.getId());
        assertThat(detail.presales()).singleElement()
                .satisfies(p -> assertThat(p.vendors()).extracting(TicketVendorInfo::name).containsExactly("팬클럽 선예매"));
        assertThat(detail.generalSales()).singleElement()
                .satisfies(s -> assertThat(s.vendors()).extracting(TicketVendorInfo::name).containsExactly("NOL", "티켓링크"));
    }

    @Test
    void 예매_일시와_예매처가_없어도_추가된다() {
        TicketSaleResponse added = service.addPresale(concert.getId(), new TicketSaleRequest(null, null));

        assertThat(added.opensAt()).isNull();
        assertThat(added.vendors()).isEmpty();
    }

    @Test
    void 예매처_이름과_링크의_공백은_지우고_빈_링크는_비운다() {
        TicketSaleResponse added = service.addGeneralSale(concert.getId(),
                request(OCT_25, new Vendor(" NOL ", " https://nol "), new Vendor("공식 안내", "  ")));

        assertThat(added.vendors()).containsExactly(
                new TicketVendorInfo("NOL", "https://nol"), new TicketVendorInfo("공식 안내", null));
    }

    @Test
    void 예매처_이름이_없으면_추가할_수_없다() {
        assertThatThrownBy(() -> service.addPresale(concert.getId(), request(OCT_20, new Vendor("  ", "https://fc"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 없는_공연에는_추가할_수_없다() {
        assertErrorCode(() -> service.addPresale(999L, request(OCT_20)), ErrorCode.CONCERT_NOT_FOUND);
        assertErrorCode(() -> service.addGeneralSale(999L, request(OCT_25)), ErrorCode.CONCERT_NOT_FOUND);
    }

    // ---------- 수정 (블록 전체 교체) ----------

    @Test
    void 수정하면_보낸_내용으로_통째로_바뀌고_예매_일시를_null로_보내면_지워진다() {
        TicketSaleResponse added = service.addGeneralSale(concert.getId(),
                request(OCT_25, new Vendor("NOL", "https://nol"), new Vendor("티켓링크", "https://link")));
        flushAndClear();

        TicketSaleResponse updated = service.updateGeneralSale(concert.getId(), added.id(),
                new TicketSaleRequest(null, List.of(new Vendor("멜론티켓", "https://melon"))));

        assertThat(updated.opensAt()).isNull();
        assertThat(updated.vendors()).containsExactly(new TicketVendorInfo("멜론티켓", "https://melon"));
    }

    @Test
    void 수정할_때_예매처를_보내지_않으면_예매처가_비워진다() {
        TicketSaleResponse added = service.addPresale(concert.getId(), request(OCT_20, new Vendor("팬클럽", "https://fc")));

        TicketSaleResponse updated = service.updatePresale(concert.getId(), added.id(), new TicketSaleRequest(OCT_20, null));

        assertThat(updated.vendors()).isEmpty();
    }

    // ---------- 다른 공연의 예매 건 ----------

    @Test
    void 다른_공연의_예매_건은_수정하거나_삭제할_수_없다() {
        Concert other = concertRepository.save(Concert.builder()
                .title("YOASOBI LIVE").category(ConcertCategory.J_POP_ARTIST).build());
        TicketSaleResponse othersPresale = service.addPresale(other.getId(), request(OCT_20));
        TicketSaleResponse othersSale = service.addGeneralSale(other.getId(), request(OCT_25));

        assertErrorCode(() -> service.updatePresale(concert.getId(), othersPresale.id(), request(OCT_25)),
                ErrorCode.TICKET_SALE_NOT_FOUND);
        assertErrorCode(() -> service.deletePresale(concert.getId(), othersPresale.id()), ErrorCode.TICKET_SALE_NOT_FOUND);
        assertErrorCode(() -> service.updateGeneralSale(concert.getId(), othersSale.id(), request(OCT_20)),
                ErrorCode.TICKET_SALE_NOT_FOUND);
        assertErrorCode(() -> service.deleteGeneralSale(concert.getId(), othersSale.id()), ErrorCode.TICKET_SALE_NOT_FOUND);
    }

    @Test
    void 선예매_id로_일반예매를_수정할_수_없다() {
        TicketSaleResponse presale = service.addPresale(concert.getId(), request(OCT_20));

        assertErrorCode(() -> service.updateGeneralSale(concert.getId(), presale.id(), request(OCT_25)),
                ErrorCode.TICKET_SALE_NOT_FOUND);
    }

    // ---------- 삭제 ----------

    @Test
    void 삭제하면_공연_상세에서_사라진다() {
        TicketSaleResponse added = service.addPresale(concert.getId(), request(OCT_20));

        service.deletePresale(concert.getId(), added.id());
        flushAndClear();

        assertThat(concertService.get(concert.getId()).presales()).isEmpty();
    }

    // ---------- 공연 상세의 정렬 ----------

    @Test
    void 공연_상세에는_예매_일시_순으로_나오고_일시가_없는_건은_뒤에_온다() {
        service.addPresale(concert.getId(), request(null, new Vendor("미정", null)));
        service.addPresale(concert.getId(), request(OCT_25, new Vendor("카드사", null)));
        service.addPresale(concert.getId(), request(OCT_20, new Vendor("팬클럽", null)));
        flushAndClear();

        assertThat(concertService.get(concert.getId()).presales())
                .extracting(TicketSaleResponse::opensAt)
                .containsExactly(OCT_20, OCT_25, null);
    }
}