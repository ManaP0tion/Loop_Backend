package com.loop.loop_backend.Concert.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.dto.admin.AdminConcertCreateRequest;
import com.loop.loop_backend.Concert.dto.admin.AdminConcertDetailResponse;
import com.loop.loop_backend.Concert.dto.admin.AdminConcertUpdateRequest;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Venue.domain.Venue;
import com.loop.loop_backend.Venue.repository.VenueRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

// 관리자 공연 등록·수정 요구사항(AD-01):
// - 직접 등록한 공연은 항상 비공개로 만들어진다.
// - 상세의 공연 시각은 기간의 모든 DAY가 순서대로 나오고, 미정인 DAY도 빠지지 않는다.
// - 수정은 null이면 변경 없음. 목록은 보내면 통째로 교체되고 []는 비운다.
// - 공개 토글은 수정 요청에 들어 있다. 저장 결과가 공개 상태면 필수값을 검사하고, 실패하면 아무것도 반영되지 않는다.
// - 이미 공개된 기존 공연(공연장 미연결)은 수정하면서 공연장을 함께 골라야 한다.
// - 없는 공연·공연장·아티스트는 404.
@DataJpaTest
class AdminConcertServiceTest {

    @Autowired private ConcertRepository concertRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private EntityManager em;

    private AdminConcertService service;
    private Venue venue;
    private Artist yuuri;

    @BeforeEach
    void setUp() {
        service = new AdminConcertService(concertRepository, artistRepository, venueRepository);
        venue = venueRepository.save(Venue.builder().name("인스파이어 아레나").address("인천광역시 중구").build());
        yuuri = artistRepository.save(Artist.builder().name("Yuuri")
                .autoFetchConcerts(true).category(ConcertCategory.J_POP_ARTIST).build());
    }

    private static AdminConcertUpdateRequest.AdminConcertUpdateRequestBuilder patch() {
        return AdminConcertUpdateRequest.builder();
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

    /** KOPIS 승인으로 만들어진 것처럼 포스터가 있는 비공개 내한 공연 */
    private Concert approvedConcert() {
        return concertRepository.save(Concert.builder()
                .kopisId("PF297519")
                .title("YUURI LIVE [서울]")
                .category(ConcertCategory.J_POP_ARTIST)
                .posterUrl("https://poster")
                .startDate(LocalDate.of(2026, 12, 5))
                .endDate(LocalDate.of(2026, 12, 6))
                .build());
    }

    // ---------- 직접 등록 ----------

    @Test
    void 직접_등록한_공연은_비공개로_만들어진다() {
        AdminConcertDetailResponse created = service.create(new AdminConcertCreateRequest(
                ConcertCategory.J_POP_ARTIST, "YUURI LIVE", List.of("유우리"),
                LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 6), Arrays.asList(LocalTime.of(18, 0), null),
                venue.getId(), List.of(yuuri.getId()), 20, false, null, List.of("PCXP-1")));

        assertThat(created.published()).isFalse();
        assertThat(created.publishedAt()).isNull();
        assertThat(created.venue().id()).isEqualTo(venue.getId());
        assertThat(created.artists()).extracting(AdminConcertDetailResponse.ArtistSummary::name).containsExactly("Yuuri");
        assertThat(created.titleAliases()).containsExactly("유우리");
        assertThat(created.productCodes()).containsExactly("PCXP-1");
    }

    // ---------- 상세 ----------

    @Test
    void 상세의_공연_시각은_기간의_모든_DAY가_순서대로_나오고_미정도_빠지지_않는다() {
        Concert concert = approvedConcert();
        concert.replaceShowtimes(Arrays.asList(null, LocalTime.of(17, 0)));
        flushAndClear();

        AdminConcertDetailResponse detail = service.get(concert.getId());

        assertThat(detail.showtimes())
                .extracting(AdminConcertDetailResponse.Showtime::day, AdminConcertDetailResponse.Showtime::date,
                        AdminConcertDetailResponse.Showtime::startTime)
                .containsExactly(
                        tuple(1, LocalDate.of(2026, 12, 5), null),
                        tuple(2, LocalDate.of(2026, 12, 6), LocalTime.of(17, 0)));
    }

    @Test
    void 없는_공연은_조회하거나_수정할_수_없다() {
        assertErrorCode(() -> service.get(999L), ErrorCode.CONCERT_NOT_FOUND);
        assertErrorCode(() -> service.update(999L, patch().build()), ErrorCode.CONCERT_NOT_FOUND);
    }

    // ---------- 부분 수정 ----------

    @Test
    void null인_필드는_바뀌지_않는다() {
        Concert concert = approvedConcert();
        concert.replaceTitleAliases(List.of("유우리"));
        flushAndClear();

        service.update(concert.getId(), patch().venueId(venue.getId()).build());
        flushAndClear();

        AdminConcertDetailResponse detail = service.get(concert.getId());
        assertThat(detail.title()).isEqualTo("YUURI LIVE [서울]");
        assertThat(detail.titleAliases()).containsExactly("유우리");
        assertThat(detail.startDate()).isEqualTo(LocalDate.of(2026, 12, 5));
        assertThat(detail.venue().id()).isEqualTo(venue.getId());
    }

    @Test
    void 목록은_보내면_통째로_교체되고_빈_목록이면_비워진다() {
        Concert concert = approvedConcert();
        concert.replaceTitleAliases(List.of("유우리", "Yuuri"));
        concert.changeArtist(yuuri);
        flushAndClear();

        service.update(concert.getId(), patch().titleAliases(List.of("유우리 내한")).artistIds(List.of()).build());
        flushAndClear();

        AdminConcertDetailResponse detail = service.get(concert.getId());
        assertThat(detail.titleAliases()).containsExactly("유우리 내한");
        assertThat(detail.artists()).isEmpty();
    }

    @Test
    void 기간과_시각을_함께_보내면_바뀐_기간_기준으로_시각을_받는다() {
        Concert concert = approvedConcert(); // 12/5~12/6
        flushAndClear();

        service.update(concert.getId(), patch()
                .endDate(LocalDate.of(2026, 12, 7))
                .showtimes(List.of(LocalTime.of(18, 0), LocalTime.of(17, 0), LocalTime.of(16, 0)))
                .build());
        flushAndClear();

        assertThat(service.get(concert.getId()).showtimes()).hasSize(3);
    }

    @Test
    void 없는_공연장이나_아티스트로는_수정할_수_없다() {
        Concert concert = approvedConcert();

        assertErrorCode(() -> service.update(concert.getId(), patch().venueId(999L).build()), ErrorCode.VENUE_NOT_FOUND);
        assertErrorCode(() -> service.update(concert.getId(), patch().artistIds(List.of(999L)).build()),
                ErrorCode.ARTIST_NOT_FOUND);
    }

    @Test
    void 원래_국내_유형인_공연도_유형을_그대로_보내면_다른_필드를_고칠_수_있다() {
        Concert domestic = concertRepository.save(Concert.builder()
                .title("잔나비 콘서트").category(ConcertCategory.DOMESTIC_ARTIST).build());
        flushAndClear();

        service.update(domestic.getId(), patch().category(ConcertCategory.DOMESTIC_ARTIST).title("잔나비 단독 콘서트").build());
        flushAndClear();

        assertThat(service.get(domestic.getId()).title()).isEqualTo("잔나비 단독 콘서트");
    }

    // ---------- 공개 전환 ----------

    @Test
    void 필수값을_채우고_공개로_바꾸면_공개되고_공개_시각이_기록된다() {
        Concert concert = approvedConcert();
        flushAndClear();

        AdminConcertDetailResponse updated = service.update(concert.getId(), patch()
                .venueId(venue.getId()).artistIds(List.of(yuuri.getId())).published(true).build());

        assertThat(updated.published()).isTrue();
        assertThat(updated.publishedAt()).isNotNull();
    }

    @Test
    void 필수값이_빠진_채로_공개하려_하면_거절되고_아무것도_반영되지_않는다() {
        Concert concert = approvedConcert(); // 공연장·아티스트 없음
        flushAndClear();

        assertThatThrownBy(() -> service.update(concert.getId(), patch()
                .title("바뀐 제목").published(true).build()))
                .isInstanceOf(IllegalArgumentException.class);
        em.clear(); // 실패한 수정이 영속성 컨텍스트에 남아 있지 않게 비우고 DB 값을 본다

        Concert reloaded = concertRepository.findById(concert.getId()).orElseThrow();
        assertThat(reloaded.isPublished()).isFalse();
        assertThat(reloaded.getTitle()).isEqualTo("YUURI LIVE [서울]");
    }

    @Test
    void 공연장이_연결되지_않은_기존_공개_공연은_공연장을_함께_골라야_수정된다() {
        Concert legacy = concertRepository.save(Concert.builder()
                .title("YUURI LIVE [서울]").category(ConcertCategory.J_POP_ARTIST).posterUrl("https://poster")
                .startDate(LocalDate.of(2026, 12, 5)).endDate(LocalDate.of(2026, 12, 6))
                .artist(yuuri).build());
        legacy.changePublished(true, java.time.LocalDateTime.now()); // 기존 공연은 공개 상태
        flushAndClear();

        assertThatThrownBy(() -> service.update(legacy.getId(), patch().title("새 제목").build()))
                .isInstanceOf(IllegalArgumentException.class);
        em.clear();

        service.update(legacy.getId(), patch().title("새 제목").venueId(venue.getId()).build());
        flushAndClear();
        assertThat(service.get(legacy.getId()).title()).isEqualTo("새 제목");
    }
}