package com.loop.loop_backend.Concert.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.CompanionPost.repository.CompanionPostRepository;
import com.loop.loop_backend.CompanionPost.service.CompanionService;
import com.loop.loop_backend.ConcertScrap.repository.ConcertScrapRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
import com.loop.loop_backend.Concert.domain.ticket.ConcertGeneralSale;
import com.loop.loop_backend.Concert.domain.ticket.ConcertPresale;
import com.loop.loop_backend.Concert.dto.ConcertPastDetailDto;
import com.loop.loop_backend.Concert.dto.ConcertPeriod;
import com.loop.loop_backend.Concert.dto.ConcertShowtimeDto;
import com.loop.loop_backend.Concert.dto.ConcertTicketSaleDto;
import com.loop.loop_backend.Concert.dto.ConcertUpcomingDetailDto;
import com.loop.loop_backend.Concert.dto.ConcertVenueDto;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Concert.repository.ticket.ConcertGeneralSaleRepository;
import com.loop.loop_backend.Concert.repository.ticket.ConcertPresaleRepository;
import com.loop.loop_backend.Storage.service.S3StorageService;
import com.loop.loop_backend.Venue.domain.Venue;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.when;

// 공연 상세 조회 3종(period / past-detail / upcoming-detail)이 요구사항대로 동작하는지 검증한다.
// - 지난 공연인지는 종료일(없으면 시작일)이 만료 기준일보다 이전인지로 판단하고, 날짜 미정 공연은 지난 공연이 아니다.
// - 상세 조회는 공연의 실제 상태와 다른 엔드포인트로 호출하면 404(CONCERT_NOT_FOUND)로 처리한다.
// - 비공개 공연은 목록에 "오픈 예정"으로만 보이고 상세에는 들어올 수 없다(403 CONCERT_NOT_OPEN).
// - 공연장: 공연장 관리의 공연장. 연결 전에 승인된 공연은 KOPIS 장소 정보로 채우고 링크는 없다.
// - 공연 시각: 기간의 모든 DAY. 시각이 입력되지 않은 이전 공연은 KOPIS 공연 시간 안내로 채운다.
// - 예매: 예매 일시가 정해진 선예매·일반예매만 일시 순으로 보인다.
// - 숙소: 노출이 켜진 공연만 딥링크가 나간다.
// 날짜는 만료 기준(오전 10시 리셋)에 걸리지 않도록 오늘에서 충분히 떨어뜨려 잡는다.
@ExtendWith(MockitoExtension.class)
class ConcertDetailServiceTest {

    private static final long CONCERT_ID = 1L;
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @Mock ConcertRepository concertRepository;
    @Mock ArtistRepository artistRepository;
    @Mock CompanionPostRepository companionPostRepository;
    @Mock CompanionService companionService;
    @Mock S3StorageService s3StorageService;
    @Mock ConcertScrapRepository concertScrapRepository;
    @Mock ConcertPresaleRepository presaleRepository;
    @Mock ConcertGeneralSaleRepository generalSaleRepository;
    @InjectMocks ConcertServiceImpl concertService;

    private Concert givenConcert(LocalDate startDate, LocalDate endDate) {
        return givenConcert(startDate, endDate, Artist.builder().name("Vaundy").build());
    }

    // 공개 공연. 비공개 공연이 필요하면 givenPrivateConcert
    private Concert givenConcert(LocalDate startDate, LocalDate endDate, Artist artist) {
        Concert concert = Concert.builder()
                .title("Vaundy ASIA ARENA TOUR")
                .venue("인스파이어 엔터테인먼트 리조트")
                .category(ConcertCategory.J_POP_ARTIST)
                .artist(artist)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        concert.changePublished(true, LocalDateTime.now());
        ReflectionTestUtils.setField(concert, "id", CONCERT_ID);
        when(concertRepository.findById(CONCERT_ID)).thenReturn(Optional.of(concert));
        return concert;
    }

    private Concert givenPrivateConcert(LocalDate startDate, LocalDate endDate) {
        Concert concert = givenConcert(startDate, endDate);
        concert.changePublished(false, LocalDateTime.now());
        return concert;
    }

    private void givenNoConcert() {
        when(concertRepository.findById(CONCERT_ID)).thenReturn(Optional.empty());
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    private static void assertNotFound(Runnable call) {
        assertError(call, ErrorCode.CONCERT_NOT_FOUND);
    }

    private static String weekday(LocalDate date) {
        return date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN); // 예: "토요일"
    }

    private static Venue linkedVenue() {
        return Venue.builder().name("인스파이어 아레나").address("인천광역시 중구").capacity(15000)
                .seatViewUrl("https://seat.example/inspire").kakaoMapUrl("https://kko.to/abc")
                .naverMapUrl("https://naver.me/xyz").latitude(37.46).longitude(126.38).build();
    }

    private static void setLegacyVenueColumns(Concert concert) {
        ReflectionTestUtils.setField(concert, "venueAddress", "인천광역시 중구 공항문화로 127 (운서동)");
        ReflectionTestUtils.setField(concert, "venueCapacity", 14483);
        ReflectionTestUtils.setField(concert, "venueLatitude", 37.4655301);
        ReflectionTestUtils.setField(concert, "venueLongitude", 126.3891177);
    }

    // ---------- period ----------

    @Test
    void period_종료일이_지난_공연은_PAST() {
        givenConcert(TODAY.minusDays(30), TODAY.minusDays(29));

        assertThat(concertService.getPeriod(CONCERT_ID).period()).isEqualTo(ConcertPeriod.PAST);
    }

    @Test
    void period_아직_시작하지_않은_공연은_UPCOMING() {
        givenConcert(TODAY.plusDays(10), TODAY.plusDays(11));

        assertThat(concertService.getPeriod(CONCERT_ID).period()).isEqualTo(ConcertPeriod.UPCOMING);
    }

    @Test
    void period_여러_날_공연이_시작은_했지만_아직_안_끝났으면_UPCOMING() {
        givenConcert(TODAY.minusDays(3), TODAY.plusDays(3));

        assertThat(concertService.getPeriod(CONCERT_ID).period()).isEqualTo(ConcertPeriod.UPCOMING);
    }

    @Test
    void period_종료일이_없으면_시작일로_판단한다() {
        givenConcert(TODAY.minusDays(30), null);

        assertThat(concertService.getPeriod(CONCERT_ID).period()).isEqualTo(ConcertPeriod.PAST);
    }

    @Test
    void period_날짜가_미정인_공연은_UPCOMING() {
        givenConcert(null, null);

        assertThat(concertService.getPeriod(CONCERT_ID).period()).isEqualTo(ConcertPeriod.UPCOMING);
    }

    @Test
    void period_없는_콘서트는_404() {
        givenNoConcert();

        assertNotFound(() -> concertService.getPeriod(CONCERT_ID));
    }

    // ---------- 비공개(오픈 예정) ----------

    @Test
    void 비공개_공연은_예정_상세에_들어올_수_없다() {
        givenPrivateConcert(TODAY.plusDays(10), TODAY.plusDays(11));

        assertError(() -> concertService.getUpcomingDetail(CONCERT_ID, null), ErrorCode.CONCERT_NOT_OPEN);
    }

    @Test
    void 비공개인_채로_기간이_지난_공연도_지난_상세에_들어올_수_없다() {
        givenPrivateConcert(TODAY.minusDays(30), TODAY.minusDays(29));

        assertError(() -> concertService.getPastDetail(CONCERT_ID, null), ErrorCode.CONCERT_NOT_OPEN);
    }

    @Test
    void 목록_요약에서_비공개_공연은_오픈_예정이고_공개_공연은_아니다() {
        givenPrivateConcert(TODAY.plusDays(10), TODAY.plusDays(11));
        assertThat(concertService.getConcertById(CONCERT_ID, null).isPreparing()).isTrue();
    }

    @Test
    void 목록_요약에서_공개_공연은_오픈_예정이_아니다() {
        givenConcert(TODAY.plusDays(10), TODAY.plusDays(11));
        assertThat(concertService.getConcertById(CONCERT_ID, null).isPreparing()).isFalse();
    }

    // ---------- past-detail ----------

    @Test
    void 지난_공연_상세는_공연명_아티스트_공연장_날짜_공연_시각을_돌려준다() {
        Concert concert = givenConcert(TODAY.minusDays(30), TODAY.minusDays(29));
        concert.changeVenue(linkedVenue());
        concert.replaceShowtimes(List.of(LocalTime.of(17, 0), LocalTime.of(16, 0)));

        ConcertPastDetailDto dto = concertService.getPastDetail(CONCERT_ID, null);

        assertThat(dto.getConcertId()).isEqualTo(CONCERT_ID);
        assertThat(dto.getTitle()).isEqualTo("Vaundy ASIA ARENA TOUR");
        assertThat(dto.getArtistName()).isEqualTo("Vaundy");
        assertThat(dto.getVenue().name()).isEqualTo("인스파이어 아레나");
        assertThat(dto.getStartDate()).isEqualTo(TODAY.minusDays(30));
        assertThat(dto.getEndDate()).isEqualTo(TODAY.minusDays(29));
        assertThat(dto.getShowtimes()).extracting(ConcertShowtimeDto::startTime)
                .containsExactly(LocalTime.of(17, 0), LocalTime.of(16, 0));
    }

    @Test
    void 예정_공연을_지난_공연_상세로_조회하면_404() {
        givenConcert(TODAY.plusDays(10), TODAY.plusDays(11));

        assertNotFound(() -> concertService.getPastDetail(CONCERT_ID, null));
    }

    @Test
    void 날짜가_미정인_공연은_지난_공연_상세로_조회하면_404() {
        givenConcert(null, null);

        assertNotFound(() -> concertService.getPastDetail(CONCERT_ID, null));
    }

    @Test
    void 지난_공연_상세_없는_콘서트는_404() {
        givenNoConcert();

        assertNotFound(() -> concertService.getPastDetail(CONCERT_ID, null));
    }

    // ---------- upcoming-detail: 기본 정보 ----------

    @Test
    void 예정_공연_상세는_공연명_아티스트_포스터_날짜_D_day를_돌려준다() {
        Artist artist = Artist.builder().name("Vaundy").build();
        ReflectionTestUtils.setField(artist, "id", 5L);
        givenConcert(TODAY.plusDays(10), TODAY.plusDays(11), artist);

        ConcertUpcomingDetailDto dto = concertService.getUpcomingDetail(CONCERT_ID, null);

        assertThat(dto.getConcertId()).isEqualTo(CONCERT_ID);
        assertThat(dto.getTitle()).isEqualTo("Vaundy ASIA ARENA TOUR");
        assertThat(dto.getArtists()).extracting("artistId", "artistName").containsExactly(tuple(5L, "Vaundy"));
        assertThat(dto.getStartDate()).isEqualTo(TODAY.plusDays(10));
        assertThat(dto.getEndDate()).isEqualTo(TODAY.plusDays(11));
        assertThat(dto.getDday()).isEqualTo(10);
    }

    @Test
    void 아티스트가_없는_공연은_아티스트가_빈_목록이다() {
        givenConcert(TODAY.plusDays(10), TODAY.plusDays(11), null);

        assertThat(concertService.getUpcomingDetail(CONCERT_ID, null).getArtists()).isEmpty();
    }

    @Test
    void 공연_당일의_D_day는_0이다() {
        givenConcert(TODAY, TODAY);

        assertThat(concertService.getUpcomingDetail(CONCERT_ID, null).getDday()).isZero();
    }

    @Test
    void 이미_시작했지만_아직_안_끝난_공연은_조회되고_D_day는_음수다() {
        givenConcert(TODAY.minusDays(1), TODAY.plusDays(1));

        assertThat(concertService.getUpcomingDetail(CONCERT_ID, null).getDday()).isEqualTo(-1);
    }

    @Test
    void 날짜가_미정인_공연은_예정_상세로_조회되고_D_day는_null_공연_시각은_빈_목록이다() {
        givenConcert(null, null);

        ConcertUpcomingDetailDto dto = concertService.getUpcomingDetail(CONCERT_ID, null);

        assertThat(dto.getTitle()).isEqualTo("Vaundy ASIA ARENA TOUR");
        assertThat(dto.getDday()).isNull();
        assertThat(dto.getShowtimes()).isEmpty();
    }

    @Test
    void 지난_공연을_예정_공연_상세로_조회하면_404() {
        givenConcert(TODAY.minusDays(30), TODAY.minusDays(29));

        assertNotFound(() -> concertService.getUpcomingDetail(CONCERT_ID, null));
    }

    @Test
    void 예정_공연_상세_없는_콘서트는_404() {
        givenNoConcert();

        assertNotFound(() -> concertService.getUpcomingDetail(CONCERT_ID, null));
    }

    // ---------- upcoming-detail: 공연장 ----------

    @Test
    void 공연장_관리의_공연장이_연결돼_있으면_그_정보와_링크가_나간다() {
        Concert concert = givenConcert(TODAY.plusDays(10), TODAY.plusDays(11));
        setLegacyVenueColumns(concert); // 연결된 공연장이 KOPIS 장소보다 우선
        concert.changeVenue(linkedVenue());

        assertThat(concertService.getUpcomingDetail(CONCERT_ID, null).getVenue()).isEqualTo(new ConcertVenueDto(
                "인스파이어 아레나", "인천광역시 중구", 15000, "https://seat.example/inspire",
                "https://kko.to/abc", "https://naver.me/xyz", 37.46, 126.38));
    }

    @Test
    void 공연장이_연결되지_않은_이전_공연은_KOPIS_장소_정보로_채우고_링크는_없다() {
        Concert concert = givenConcert(TODAY.plusDays(10), TODAY.plusDays(11));
        setLegacyVenueColumns(concert);

        assertThat(concertService.getUpcomingDetail(CONCERT_ID, null).getVenue()).isEqualTo(new ConcertVenueDto(
                "인스파이어 엔터테인먼트 리조트", "인천광역시 중구 공항문화로 127 (운서동)", 14483,
                null, null, null, 37.4655301, 126.3891177));
    }

    @Test
    void 공연장_정보가_전혀_없는_공연은_공연장이_null이다() {
        Concert concert = givenConcert(TODAY.plusDays(10), TODAY.plusDays(11));
        ReflectionTestUtils.setField(concert, "venue", null);

        assertThat(concertService.getUpcomingDetail(CONCERT_ID, null).getVenue()).isNull();
    }

    // ---------- upcoming-detail: DAY별 공연 시각 ----------

    @Test
    void 공연_시각은_기간의_모든_DAY가_나오고_미정인_DAY는_시각이_비어_있다() {
        Concert concert = givenConcert(TODAY.plusDays(10), TODAY.plusDays(11));
        concert.replaceShowtimes(Arrays.asList(LocalTime.of(18, 0), null));

        assertThat(concertService.getUpcomingDetail(CONCERT_ID, null).getShowtimes())
                .extracting(ConcertShowtimeDto::day, ConcertShowtimeDto::date, ConcertShowtimeDto::startTime)
                .containsExactly(
                        tuple(1, TODAY.plusDays(10), LocalTime.of(18, 0)),
                        tuple(2, TODAY.plusDays(11), null));
    }

    @Test
    void 공연_시각이_입력되지_않은_이전_공연은_KOPIS_공연_시간_안내로_채운다() {
        LocalDate day1 = TODAY.plusDays(10);
        LocalDate day2 = TODAY.plusDays(11);
        Concert concert = givenConcert(day1, day2);
        ReflectionTestUtils.setField(concert, "showtime", weekday(day1) + "(17:00), " + weekday(day2) + "(16:00)");

        assertThat(concertService.getUpcomingDetail(CONCERT_ID, null).getShowtimes())
                .extracting(ConcertShowtimeDto::startTime)
                .containsExactly(LocalTime.of(17, 0), LocalTime.of(16, 0));
    }

    @Test
    void 관리자가_공연_시각을_모두_비운_공연은_KOPIS_값으로_되살아나지_않는다() {
        LocalDate day1 = TODAY.plusDays(10);
        LocalDate day2 = TODAY.plusDays(11);
        Concert concert = givenConcert(day1, day2);
        ReflectionTestUtils.setField(concert, "showtime", weekday(day1) + "(17:00), " + weekday(day2) + "(16:00)");
        concert.replaceShowtimes(Arrays.asList(null, null));

        assertThat(concertService.getUpcomingDetail(CONCERT_ID, null).getShowtimes())
                .extracting(ConcertShowtimeDto::startTime)
                .containsOnlyNulls();
    }

    // ---------- upcoming-detail: 예매 ----------

    @Test
    void 예매는_일시가_정해진_블록만_일시_순으로_나간다() {
        Concert concert = givenConcert(TODAY.plusDays(30), TODAY.plusDays(31));
        LocalDateTime oct20 = LocalDateTime.of(2026, 10, 20, 20, 0);
        LocalDateTime oct25 = LocalDateTime.of(2026, 10, 25, 20, 0);
        List<TicketVendorInfo> nol = List.of(new TicketVendorInfo("NOL", "https://nol"));
        when(presaleRepository.findByConcert_Id(CONCERT_ID)).thenReturn(List.of(
                ConcertPresale.builder().concert(concert).opensAt(oct25).vendors(nol).build(),
                ConcertPresale.builder().concert(concert).opensAt(oct20).vendors(nol).build()));
        when(generalSaleRepository.findByConcert_Id(CONCERT_ID)).thenReturn(List.of(
                ConcertGeneralSale.builder().concert(concert).vendors(nol).build())); // 일시 미정

        ConcertUpcomingDetailDto dto = concertService.getUpcomingDetail(CONCERT_ID, null);

        assertThat(dto.getPresales()).extracting(ConcertTicketSaleDto::opensAt).containsExactly(oct20, oct25);
        assertThat(dto.getPresales().get(0).vendors()).containsExactlyElementsOf(nol);
        assertThat(dto.getGeneralSales()).isEmpty();
    }

    @Test
    void 이전_공연의_KOPIS_예매처는_예매_일시가_없어_보이지_않는다() {
        Concert concert = givenConcert(TODAY.plusDays(10), TODAY.plusDays(11));
        ReflectionTestUtils.setField(concert, "ticketVendors", List.of(new TicketVendorInfo("인터파크", "http://a.example/1")));

        ConcertUpcomingDetailDto dto = concertService.getUpcomingDetail(CONCERT_ID, null);

        assertThat(dto.getPresales()).isEmpty();
        assertThat(dto.getGeneralSales()).isEmpty();
    }

    // ---------- upcoming-detail: 숙소·관련 상품 ----------

    @Test
    void 숙소_노출이_켜진_공연만_딥링크가_나간다() {
        Concert concert = givenConcert(TODAY.plusDays(10), TODAY.plusDays(11));
        concert.changeLodgingUrl("https://trip.example/deeplink");

        assertThat(concertService.getUpcomingDetail(CONCERT_ID, null).getLodgingUrl()).isNull();

        concert.changeLodgingVisible(true);
        assertThat(concertService.getUpcomingDetail(CONCERT_ID, null).getLodgingUrl())
                .isEqualTo("https://trip.example/deeplink");
    }

    @Test
    void 관련_상품_코드가_나가고_없으면_빈_목록이다() {
        Concert concert = givenConcert(TODAY.plusDays(10), TODAY.plusDays(11));
        assertThat(concertService.getUpcomingDetail(CONCERT_ID, null).getProductCodes()).isEmpty();

        concert.replaceProductCodes(List.of("PCXP-51237"));
        assertThat(concertService.getUpcomingDetail(CONCERT_ID, null).getProductCodes()).containsExactly("PCXP-51237");
    }

    // ---------- 스크랩 여부 ----------
    // 상세 화면의 스크랩 버튼 상태: 로그인 유저가 스크랩한 공연이면 true, 아니면 false. 비로그인 조회는 항상 false.

    private static final long USER_ID = 7L;

    @Test
    void 스크랩한_예정_공연의_상세는_scrapped가_true다() {
        givenConcert(TODAY.plusDays(10), TODAY.plusDays(11));
        when(concertScrapRepository.existsByUser_IdAndConcert_Id(USER_ID, CONCERT_ID)).thenReturn(true);

        assertThat(concertService.getUpcomingDetail(CONCERT_ID, USER_ID).isScrapped()).isTrue();
    }

    @Test
    void 스크랩하지_않은_예정_공연의_상세는_scrapped가_false다() {
        givenConcert(TODAY.plusDays(10), TODAY.plusDays(11));
        when(concertScrapRepository.existsByUser_IdAndConcert_Id(USER_ID, CONCERT_ID)).thenReturn(false);

        assertThat(concertService.getUpcomingDetail(CONCERT_ID, USER_ID).isScrapped()).isFalse();
    }

    @Test
    void 비로그인으로_예정_공연_상세를_조회하면_scrapped는_false다() {
        givenConcert(TODAY.plusDays(10), TODAY.plusDays(11));

        assertThat(concertService.getUpcomingDetail(CONCERT_ID, null).isScrapped()).isFalse();
    }

    @Test
    void 스크랩한_지난_공연의_상세는_scrapped가_true다() {
        givenConcert(TODAY.minusDays(30), TODAY.minusDays(29));
        when(concertScrapRepository.existsByUser_IdAndConcert_Id(USER_ID, CONCERT_ID)).thenReturn(true);

        assertThat(concertService.getPastDetail(CONCERT_ID, USER_ID).isScrapped()).isTrue();
    }

    @Test
    void 스크랩하지_않은_지난_공연의_상세는_scrapped가_false다() {
        givenConcert(TODAY.minusDays(30), TODAY.minusDays(29));
        when(concertScrapRepository.existsByUser_IdAndConcert_Id(USER_ID, CONCERT_ID)).thenReturn(false);

        assertThat(concertService.getPastDetail(CONCERT_ID, USER_ID).isScrapped()).isFalse();
    }

    @Test
    void 비로그인으로_지난_공연_상세를_조회하면_scrapped는_false다() {
        givenConcert(TODAY.minusDays(30), TODAY.minusDays(29));

        assertThat(concertService.getPastDetail(CONCERT_ID, null).isScrapped()).isFalse();
    }
}