package com.loop.loop_backend.Concert.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.domain.Nationality;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.dto.admin.AdminConcertCreateRequest;
import com.loop.loop_backend.Concert.dto.admin.AdminConcertDetailResponse;
import com.loop.loop_backend.Concert.dto.admin.AdminConcertUpdateRequest;
import com.loop.loop_backend.Concert.dto.admin.TicketSaleRequest;
import com.loop.loop_backend.Concert.dto.admin.TicketSaleResponse;
import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
import com.loop.loop_backend.Concert.repository.AdminConcertRepository;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Concert.repository.ticket.ConcertGeneralSaleRepository;
import com.loop.loop_backend.Concert.repository.ticket.ConcertPresaleRepository;
import com.loop.loop_backend.Performance.domain.Performance;
import com.loop.loop_backend.Performance.repository.PerformanceRepository;
import com.loop.loop_backend.Performance.service.PerformanceSync;
import com.loop.loop_backend.Setlist.repository.SetlistRepository;
import com.loop.loop_backend.Setlist.repository.SetlistVoteRepository;
import com.loop.loop_backend.Setlist.service.SetlistDataGuard;
import com.loop.loop_backend.Setlist.domain.Setlist;
import com.loop.loop_backend.Setlist.domain.SetlistType;
import com.loop.loop_backend.Song.domain.Song;
import com.loop.loop_backend.Venue.domain.Venue;
import com.loop.loop_backend.Venue.repository.VenueRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
// - 예매 정보(선예매·일반예매)도 수정 요청으로 함께 받는다. 보내면 통째로 교체, []는 비움, null은 유지. 예매처 이름은 필수.
// - 이미 공개된 기존 공연(공연장 미연결)은 수정하면서 공연장을 함께 골라야 한다.
// - 없는 공연·공연장·아티스트는 404.
// - 페스티벌 기간이 줄면 범위를 벗어난 DAY의 라인업이, 페스티벌이 아니게 되면 라인업 전체가 지워진다(AD-04).
@DataJpaTest
class AdminConcertServiceTest {

    @Autowired private ConcertRepository concertRepository;
    @Autowired private AdminConcertRepository adminConcertRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private PerformanceRepository performanceRepository;
    @Autowired private SetlistRepository setlistRepository;
    @Autowired private SetlistVoteRepository setlistVoteRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private ConcertPresaleRepository presaleRepository;
    @Autowired private ConcertGeneralSaleRepository generalSaleRepository;
    @Autowired private EntityManager em;

    private AdminConcertService service;
    private Venue venue;
    private Artist yuuri;

    @BeforeEach
    void setUp() {
        service = new AdminConcertService(concertRepository, adminConcertRepository, artistRepository, venueRepository,
                presaleRepository, generalSaleRepository, performanceRepository,
                new PerformanceSync(performanceRepository, new SetlistDataGuard(setlistRepository, setlistVoteRepository)));
        venue = venueRepository.save(Venue.builder().name("인스파이어 아레나").address("인천광역시 중구").build());
        yuuri = artistRepository.save(Artist.builder().name("Yuuri")
                .autoFetchConcerts(true).nationality(Nationality.JP).build());
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
                .category(ConcertCategory.STAGE)
                .posterUrl("https://poster")
                .startDate(LocalDate.of(2026, 12, 5))
                .endDate(LocalDate.of(2026, 12, 6))
                .build());
    }

    // ---------- 직접 등록 ----------

    @Test
    void 직접_등록한_공연은_비공개로_만들어진다() {
        AdminConcertDetailResponse created = service.create(new AdminConcertCreateRequest(
                ConcertCategory.STAGE, "YUURI LIVE", List.of("유우리"),
                LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 6), Arrays.asList(LocalTime.of(18, 0), null),
                venue.getId(), List.of(yuuri.getId()), 20, false, null, "https://yuuri-live.example.com", null, null,
                List.of(sale(null, "공식 안내")), List.of(sale(OCT_25, "NOL"))));

        assertThat(created.published()).isFalse();
        assertThat(created.publishedAt()).isNull();
        assertThat(created.presales()).singleElement()
                .satisfies(p -> assertThat(p.vendors()).extracting(TicketVendorInfo::name).containsExactly("공식 안내"));
        assertThat(created.generalSales()).singleElement()
                .satisfies(s -> assertThat(s.opensAt()).isEqualTo(OCT_25));
        assertThat(created.venue().id()).isEqualTo(venue.getId());
        assertThat(created.artists()).extracting(AdminConcertDetailResponse.ArtistSummary::name).containsExactly("Yuuri");
        assertThat(created.titleAliases()).containsExactly("유우리");
        assertThat(created.officialSiteUrl()).isEqualTo("https://yuuri-live.example.com");
    }

    // ---------- 특설 공식 사이트 ----------

    @Test
    void 공식_사이트는_수정으로_등록하고_null이면_유지된다() {
        Concert concert = approvedConcert();

        service.update(concert.getId(), patch().officialSiteUrl("  https://yuuri-live.example.com  ").build());
        AdminConcertDetailResponse kept = service.update(concert.getId(), patch().title("YUURI LIVE 2026").build());

        assertThat(kept.officialSiteUrl()).isEqualTo("https://yuuri-live.example.com");
    }

    @Test
    void 공식_사이트를_빈_문자열로_보내면_비워진다() {
        Concert concert = approvedConcert();
        service.update(concert.getId(), patch().officialSiteUrl("https://yuuri-live.example.com").build());

        AdminConcertDetailResponse cleared = service.update(concert.getId(), patch().officialSiteUrl("").build());

        assertThat(cleared.officialSiteUrl()).isNull();
    }

    @Test
    void 공식_사이트는_http_https_주소만_받는다() {
        Concert concert = approvedConcert();

        for (String invalid : List.of("javascript:alert(1)", "yuuri-live.example.com", "ftp://yuuri-live.example.com")) {
            assertThatThrownBy(() -> service.update(concert.getId(), patch().officialSiteUrl(invalid).build()))
                    .as(invalid)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(service.update(concert.getId(), patch().officialSiteUrl("http://yuuri-live.example.com").build())
                .officialSiteUrl()).isEqualTo("http://yuuri-live.example.com");
    }

    // ---------- 공연 공식 채널 ----------
    // 공연 공식 채널(인스타그램·X)은 페스티벌용이지만 유형과 상관없이 저장한다. 홈페이지는 공식 사이트(officialSiteUrl)를 쓴다.
    // 공식 사이트와 같은 규칙: http/https 주소만, 수정에서 null은 유지, ""는 비움.

    @Test
    @DisplayName("직접 등록할 때 보낸 공연 공식 채널이 저장된다")
    void create_WithOfficialChannels_SavesBoth() {
        // when
        AdminConcertDetailResponse created = service.create(new AdminConcertCreateRequest(
                ConcertCategory.FESTIVAL, "SUMMER SONIC 2026", null, null, null, null,
                null, null, null, false, null, "https://www.summersonic.com",
                "https://www.instagram.com/summersonic_official", "https://x.com/summer_sonic", null, null));
        flushAndClear();

        // then
        AdminConcertDetailResponse found = service.get(created.id());
        assertThat(found.officialSiteUrl()).isEqualTo("https://www.summersonic.com");
        assertThat(found.instagramUrl()).isEqualTo("https://www.instagram.com/summersonic_official");
        assertThat(found.xUrl()).isEqualTo("https://x.com/summer_sonic");
    }

    @Test
    @DisplayName("공연 공식 채널은 수정으로 등록하고, 보내지 않은(null) 채널은 유지된다")
    void update_OfficialChannels_NullKeepsValue() {
        // given
        Concert concert = approvedConcert();
        service.update(concert.getId(), patch()
                .instagramUrl("  https://www.instagram.com/yuuri_live  ").xUrl("https://x.com/yuuri_live").build());

        // when - 인스타그램만 바꾼다
        AdminConcertDetailResponse updated = service.update(concert.getId(),
                patch().instagramUrl("https://www.instagram.com/yuuri_live2").build());

        // then
        assertThat(updated.instagramUrl()).isEqualTo("https://www.instagram.com/yuuri_live2");
        assertThat(updated.xUrl()).isEqualTo("https://x.com/yuuri_live");
    }

    @Test
    @DisplayName("공연 공식 채널을 빈 문자열로 보내면 비워진다")
    void update_OfficialChannelsWithEmptyString_ClearsValue() {
        // given
        Concert concert = approvedConcert();
        service.update(concert.getId(), patch()
                .instagramUrl("https://www.instagram.com/yuuri_live").xUrl("https://x.com/yuuri_live").build());

        // when
        AdminConcertDetailResponse cleared = service.update(concert.getId(), patch().instagramUrl("").xUrl("").build());

        // then
        assertThat(cleared.instagramUrl()).isNull();
        assertThat(cleared.xUrl()).isNull();
    }

    @Test
    @DisplayName("공연 공식 채널은 http/https 주소만 받는다")
    void update_OfficialChannelsWithNonHttpUrl_ThrowsException() {
        // given
        Concert concert = approvedConcert();

        // when & then
        for (String invalid : List.of("javascript:alert(1)", "instagram.com/yuuri_live", "ftp://x.com/yuuri_live")) {
            assertThatThrownBy(() -> service.update(concert.getId(), patch().instagramUrl(invalid).build()))
                    .as("instagramUrl " + invalid).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.update(concert.getId(), patch().xUrl(invalid).build()))
                    .as("xUrl " + invalid).isInstanceOf(IllegalArgumentException.class);
        }
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

    // ---------- 예매 정보 (수정 화면의 저장 버튼으로 함께 저장) ----------

    private static final LocalDateTime OCT_20 = LocalDateTime.of(2026, 10, 20, 20, 0);
    private static final LocalDateTime OCT_25 = LocalDateTime.of(2026, 10, 25, 20, 0);

    private static TicketSaleRequest sale(LocalDateTime opensAt, String... vendorNames) {
        return new TicketSaleRequest(opensAt, Arrays.stream(vendorNames)
                .map(name -> new TicketSaleRequest.Vendor(name, "https://" + name)).toList());
    }

    @Test
    void 예매_정보를_보내면_선예매_일반예매가_각각_통째로_교체된다() {
        Concert concert = approvedConcert();
        flushAndClear();
        service.update(concert.getId(), patch()
                .presales(List.of(sale(OCT_20, "팬클럽")))
                .generalSales(List.of(sale(OCT_25, "NOL", "티켓링크")))
                .build());
        flushAndClear();

        service.update(concert.getId(), patch()
                .presales(List.of(sale(OCT_20, "팬클럽"), sale(null, "카드사")))
                .generalSales(List.of(sale(null, "멜론티켓")))
                .build());
        flushAndClear();

        AdminConcertDetailResponse detail = service.get(concert.getId());
        assertThat(detail.presales()).extracting(TicketSaleResponse::opensAt).containsExactly(OCT_20, null); // 일시 순, 미정은 뒤
        assertThat(detail.generalSales()).singleElement().satisfies(s -> {
            assertThat(s.opensAt()).isNull();
            assertThat(s.vendors()).extracting(TicketVendorInfo::name).containsExactly("멜론티켓");
        });
    }

    @Test
    void 예매_정보를_빈_목록으로_보내면_비워지고_null이면_그대로다() {
        Concert concert = approvedConcert();
        flushAndClear();
        service.update(concert.getId(), patch()
                .presales(List.of(sale(OCT_20, "팬클럽")))
                .generalSales(List.of(sale(OCT_25, "NOL")))
                .build());
        flushAndClear();

        service.update(concert.getId(), patch().presales(List.of()).title("제목만 수정").build());
        flushAndClear();

        AdminConcertDetailResponse detail = service.get(concert.getId());
        assertThat(detail.presales()).isEmpty();             // [] → 비움
        assertThat(detail.generalSales()).hasSize(1);        // null → 유지
    }

    @Test
    void 예매처_이름이_없으면_거절되고_예매_정보도_바뀌지_않는다() {
        Concert concert = approvedConcert();
        service.update(concert.getId(), patch().generalSales(List.of(sale(OCT_25, "NOL"))).build());
        flushAndClear();

        assertThatThrownBy(() -> service.update(concert.getId(), patch()
                .generalSales(List.of(new TicketSaleRequest(OCT_20, List.of(new TicketSaleRequest.Vendor(" ", "https://x")))))
                .build()))
                .isInstanceOf(IllegalArgumentException.class);
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
                .title("YUURI LIVE [서울]").category(ConcertCategory.STAGE).posterUrl("https://poster")
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

    // ---------- 라인업 정리 (AD-04) ----------

    private Concert festivalWithLineupOnEveryDay() {
        Concert festival = concertRepository.save(Concert.builder()
                .title("SUMMER SONIC").category(ConcertCategory.FESTIVAL)
                .startDate(LocalDate.of(2026, 11, 20)).endDate(LocalDate.of(2026, 11, 22))
                .build());
        // DAY마다 다른 아티스트 1팀(공연당 아티스트 1항목)
        for (int day = 1; day <= 3; day++) {
            Artist artist = artistRepository.save(Artist.builder().name("DAY" + day + " 아티스트").build());
            performanceRepository.save(Performance.builder().concert(festival).artist(artist).day(day).displayOrder(day).build());
        }
        flushAndClear();
        return festival;
    }

    private List<Integer> lineupDays(Concert concert) {
        return performanceRepository.findByConcertIdOrderByDisplayOrderAsc(concert.getId()).stream()
                .flatMap(p -> p.getDays().stream()).toList();
    }

    @Test
    void 페스티벌_기간이_줄면_범위를_벗어난_DAY의_라인업만_지워진다() {
        Concert festival = festivalWithLineupOnEveryDay();

        service.update(festival.getId(), patch().endDate(LocalDate.of(2026, 11, 21)).build());
        flushAndClear();

        assertThat(lineupDays(festival)).containsExactly(1, 2);
    }

    @Test
    void 기간을_건드리지_않는_수정은_라인업을_지우지_않는다() {
        Concert festival = festivalWithLineupOnEveryDay();

        service.update(festival.getId(), patch().title("SUMMER SONIC 2026").build());
        flushAndClear();

        assertThat(lineupDays(festival)).containsExactly(1, 2, 3);
    }

    @Test
    void 페스티벌을_내한_공연으로_바꾸면_라인업이_모두_지워진다() {
        Concert festival = festivalWithLineupOnEveryDay();

        service.update(festival.getId(), patch().category(ConcertCategory.STAGE).build());
        flushAndClear();

        assertThat(lineupDays(festival)).isEmpty();
    }

    @Test
    void 수정이_실패하면_라인업도_지워지지_않는다() {
        Concert festival = festivalWithLineupOnEveryDay();

        assertThatThrownBy(() -> service.update(festival.getId(), patch()
                .endDate(LocalDate.of(2026, 11, 21)).published(true).build())) // 포스터·공연장이 없어 공개 불가
                .isInstanceOf(IllegalArgumentException.class);
        em.clear();

        assertThat(lineupDays(festival)).containsExactly(1, 2, 3);
    }

    // ---------- 출연 정보 동기화 ----------

    /** 출연 정보에 실제 셋리스트를 붙인다(셋리스트 데이터가 있는 상태). */
    private void attachSetlist(Performance performance) {
        Song song = Song.builder().artist(performance.getArtist()).titleOriginal("曲").sortOrder(1).build();
        em.persist(song);
        em.persist(Setlist.builder().performance(performance).type(SetlistType.ACTUAL).songs(List.of(song)).build());
        flushAndClear();
    }

    private Performance soloPerformanceOf(Concert concert) {
        return performanceRepository.findFirstByConcertId(concert.getId()).orElseThrow();
    }

    @Test
    @DisplayName("아티스트를 지정해 내한 공연을 만들면 출연 정보가 생기고 예상 곡 수가 저장된다")
    void create_WithArtistAndExpectedCount_CreatesSoloPerformance() {
        // when
        AdminConcertDetailResponse created = service.create(new AdminConcertCreateRequest(
                ConcertCategory.STAGE, "YUURI LIVE", null,
                LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 5), null,
                null, List.of(yuuri.getId()), 18, false, null, null, null, null, null, null));
        flushAndClear();

        // then
        Performance solo = performanceRepository.findFirstByConcertId(created.id()).orElseThrow();
        assertThat(solo.getArtist().getId()).isEqualTo(yuuri.getId());
        assertThat(solo.getExpectedSongCount()).isEqualTo(18);
        assertThat(created.performanceId()).isEqualTo(solo.getId());
        assertThat(created.expectedSongCount()).isEqualTo(18);
    }

    @Test
    @DisplayName("내한 공연의 아티스트를 바꾸면 출연 정보는 유지되고 아티스트만 바뀐다")
    void update_WhenArtistChanged_KeepsPerformanceAndChangesArtist() {
        // given
        Concert concert = approvedConcert();
        service.update(concert.getId(), patch().artistIds(List.of(yuuri.getId())).build());
        flushAndClear();
        Long performanceId = soloPerformanceOf(concert).getId();
        Artist ado = artistRepository.save(Artist.builder().name("Ado").nationality(Nationality.JP).build());

        // when
        service.update(concert.getId(), patch().artistIds(List.of(ado.getId())).build());
        flushAndClear();

        // then
        Performance solo = soloPerformanceOf(concert);
        assertThat(solo.getId()).isEqualTo(performanceId);
        assertThat(solo.getArtist().getId()).isEqualTo(ado.getId());
    }

    @Test
    @DisplayName("내한 공연의 아티스트를 비우면 출연 정보가 지워지고, 셋리스트가 있으면 409로 막는다")
    void update_WhenArtistCleared_DeletesPerformanceUnlessSetlistExists() {
        // given
        Concert empty = approvedConcert();
        service.update(empty.getId(), patch().artistIds(List.of(yuuri.getId())).build());
        // approvedConcert는 KOPIS ID가 같아 (KOPIS ID, 아티스트) 유니크를 피하려고 다른 아티스트를 쓴다
        Artist ado = artistRepository.save(Artist.builder().name("Ado").nationality(Nationality.JP).build());
        Concert withSetlist = approvedConcert();
        service.update(withSetlist.getId(), patch().artistIds(List.of(ado.getId())).build());
        flushAndClear();
        attachSetlist(soloPerformanceOf(withSetlist));

        // when
        service.update(empty.getId(), patch().artistIds(List.of()).build());
        flushAndClear();

        // then
        assertThat(performanceRepository.findFirstByConcertId(empty.getId())).isEmpty();
        assertErrorCode(() -> service.update(withSetlist.getId(), patch().artistIds(List.of()).build()),
                ErrorCode.PERFORMANCE_HAS_SETLIST_DATA);
    }

    @Test
    @DisplayName("셋리스트가 있는 내한 공연을 페스티벌로 바꾸면 409이고 출연 정보는 지워지지 않는다")
    void update_WhenSoloWithSetlistChangedToFestival_ThrowsConflict() {
        // given
        Concert concert = approvedConcert();
        service.update(concert.getId(), patch().artistIds(List.of(yuuri.getId())).build());
        flushAndClear();
        Performance solo = soloPerformanceOf(concert);
        attachSetlist(solo);

        // when & then - 공연 유형 롤백은 요청 트랜잭션이 맡는다(@DataJpaTest는 테스트 전체가 한 트랜잭션이라 여기서 확인하지 않음)
        assertErrorCode(() -> service.update(concert.getId(), patch().category(ConcertCategory.FESTIVAL).build()),
                ErrorCode.PERFORMANCE_HAS_SETLIST_DATA);
        em.clear();
        assertThat(performanceRepository.findById(solo.getId())).isPresent();
    }

    @Test
    @DisplayName("셋리스트가 있는 헤드라이너의 DAY가 기간 밖으로 밀리면 409이고 라인업은 그대로다")
    void update_WhenPeriodShrinkRemovesHeadlinerWithSetlist_ThrowsConflict() {
        // given
        Concert festival = festivalWithLineupOnEveryDay();
        Performance day3 = performanceRepository.findByConcertIdOrderByDisplayOrderAsc(festival.getId()).get(2);
        day3.changeHeadliner(true);
        em.flush();
        attachSetlist(day3);

        // when & then
        assertErrorCode(() -> service.update(festival.getId(), patch().endDate(LocalDate.of(2026, 11, 21)).build()),
                ErrorCode.PERFORMANCE_HAS_SETLIST_DATA);
        em.clear();
        assertThat(lineupDays(festival)).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("예상 곡 수는 페스티벌이나 아티스트 없는 내한 공연에는 정할 수 없다")
    void update_WithExpectedCountOnFestivalOrNoArtist_ThrowsInvalidInput() {
        // given
        Concert festival = festivalWithLineupOnEveryDay();
        Concert noArtist = approvedConcert();

        // when & then
        assertThatThrownBy(() -> service.update(festival.getId(), patch().expectedSongCount(12).build()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update(noArtist.getId(), patch().expectedSongCount(12).build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- 숙소 (공연장 단위) ----------
    // 공연 화면에서 받은 숙소 노출 여부·딥링크는 연결된 공연장에 저장되어, 같은 공연장의 모든 공연에 적용된다.

    private static final String DEEPLINK = "https://trip.example/deeplink?trip_sub=PF297519";

    @Test
    @DisplayName("공연 수정으로 넣은 숙소는 연결된 공연장에 저장되어 같은 공연장의 다른 공연에도 나온다")
    void update_WithLodging_SavesToVenueAndAppliesToOtherConcerts() {
        // given
        Concert edited = approvedConcert();
        Concert sameVenue = approvedConcert();
        service.update(edited.getId(), patch().venueId(venue.getId()).build());
        service.update(sameVenue.getId(), patch().venueId(venue.getId()).build());

        // when
        service.update(edited.getId(), patch().lodgingVisible(true).lodgingUrl(DEEPLINK).build());
        flushAndClear();

        // then
        AdminConcertDetailResponse other = service.get(sameVenue.getId());
        assertThat(other.lodgingVisible()).isTrue();
        assertThat(other.lodgingUrl()).isEqualTo(DEEPLINK);
        Venue saved = venueRepository.findById(venue.getId()).orElseThrow();
        assertThat(saved.visibleLodgingUrl()).isEqualTo(DEEPLINK);
    }

    @Test
    @DisplayName("같은 요청에서 공연장을 바꾸면 숙소는 새 공연장에 저장되고 이전 공연장은 그대로다")
    void update_WithVenueChangeAndLodging_SavesToNewVenue() {
        // given
        Concert concert = approvedConcert();
        service.update(concert.getId(), patch().venueId(venue.getId()).build());
        Venue newVenue = venueRepository.save(Venue.builder().name("KSPO DOME").address("서울특별시 송파구").build());

        // when
        service.update(concert.getId(), patch().venueId(newVenue.getId()).lodgingVisible(true).lodgingUrl(DEEPLINK).build());
        flushAndClear();

        // then
        assertThat(venueRepository.findById(newVenue.getId()).orElseThrow().visibleLodgingUrl()).isEqualTo(DEEPLINK);
        assertThat(venueRepository.findById(venue.getId()).orElseThrow().getLodgingUrl()).isNull();
    }

    @Test
    @DisplayName("직접 등록할 때 보낸 숙소는 선택한 공연장에 저장된다")
    void create_WithVenueAndLodging_SavesToVenue() {
        // when
        AdminConcertDetailResponse created = service.create(new AdminConcertCreateRequest(
                ConcertCategory.STAGE, "YUURI LIVE", null, null, null, null,
                venue.getId(), null, null, true, DEEPLINK, null, null, null, null, null));
        flushAndClear();

        // then
        assertThat(created.lodgingVisible()).isTrue();
        assertThat(created.lodgingUrl()).isEqualTo(DEEPLINK);
        assertThat(venueRepository.findById(venue.getId()).orElseThrow().visibleLodgingUrl()).isEqualTo(DEEPLINK);
    }

    @Test
    @DisplayName("공연장이 없는 공연에서 숙소 노출을 켜거나 딥링크를 넣으면 400(IllegalArgumentException)이다")
    void update_WithoutVenue_WithLodging_ThrowsException() {
        // given
        Concert concert = approvedConcert();

        // when & then
        assertThatThrownBy(() -> service.update(concert.getId(), patch().lodgingVisible(true).build()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update(concert.getId(), patch().lodgingUrl(DEEPLINK).build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("여러 DAY에 나오는 출연 정보는 기간이 줄면 범위 밖 DAY만 빠지고 출연 정보는 남는다")
    void update_WhenPeriodShrinks_DropsOnlyOutOfRangeDaysOfMultiDayPerformance() {
        // given - DAY2·DAY3에 나오는 헤드라이너
        Concert festival = concertRepository.save(Concert.builder()
                .title("SUMMER SONIC").category(ConcertCategory.FESTIVAL)
                .startDate(LocalDate.of(2026, 11, 20)).endDate(LocalDate.of(2026, 11, 22))
                .build());
        Performance headliner = Performance.builder().concert(festival).artist(yuuri).day(2).displayOrder(1).build();
        headliner.addDay(3);
        headliner.changeHeadliner(true);
        performanceRepository.save(headliner);
        flushAndClear();
        attachSetlist(performanceRepository.findById(headliner.getId()).orElseThrow());

        // when - DAY3이 기간 밖으로 밀린다. 출연 정보는 지워지지 않으므로 셋리스트가 있어도 409가 아니다
        service.update(festival.getId(), patch().endDate(LocalDate.of(2026, 11, 21)).build());
        flushAndClear();

        // then
        assertThat(performanceRepository.findById(headliner.getId()).orElseThrow().getDays()).containsExactly(2);
    }

    @Test
    @DisplayName("공연장이 없는 공연도 숙소 기본값(노출 끔, 빈 딥링크)을 보내면 저장된다")
    void update_WithoutVenue_WithDefaultLodgingValues_Succeeds() {
        // given
        Concert concert = approvedConcert();

        // when
        AdminConcertDetailResponse updated = service.update(concert.getId(),
                patch().title("YUURI LIVE 2026").lodgingVisible(false).lodgingUrl("").build());

        // then
        assertThat(updated.title()).isEqualTo("YUURI LIVE 2026");
        assertThat(updated.lodgingVisible()).isFalse();
        assertThat(updated.lodgingUrl()).isNull();
    }

    @Test
    @DisplayName("공연 수정에서 딥링크 없이 숙소 노출을 켜면 400(IllegalArgumentException)이고 공연장은 바뀌지 않는다")
    void update_VisibleWithoutUrl_ThrowsException() {
        // given
        Concert concert = approvedConcert();
        service.update(concert.getId(), patch().venueId(venue.getId()).build());

        // when & then
        assertThatThrownBy(() -> service.update(concert.getId(), patch().lodgingVisible(true).build()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(venue.isLodgingVisible()).isFalse();
    }
}
