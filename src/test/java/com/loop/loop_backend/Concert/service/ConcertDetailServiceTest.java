package com.loop.loop_backend.Concert.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.CompanionPost.repository.CompanionPostRepository;
import com.loop.loop_backend.CompanionPost.service.CompanionService;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
import com.loop.loop_backend.Concert.dto.ConcertPastDetailDto;
import com.loop.loop_backend.Concert.dto.ConcertPeriod;
import com.loop.loop_backend.Concert.dto.ConcertUpcomingDetailDto;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Storage.service.S3StorageService;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// 공연 상세 조회 3종(period / past-detail / upcoming-detail)이 요구사항대로 동작하는지 검증한다.
// - 지난 공연인지는 종료일(없으면 시작일)이 만료 기준일보다 이전인지로 판단하고, 날짜 미정 공연은 지난 공연이 아니다.
// - 상세 조회는 공연의 실제 상태와 다른 엔드포인트로 호출하면 404(CONCERT_NOT_FOUND)로 처리한다.
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
    @InjectMocks ConcertServiceImpl concertService;

    private Concert givenConcert(LocalDate startDate, LocalDate endDate) {
        return givenConcert(startDate, endDate, Artist.builder().name("Vaundy").build());
    }

    private Concert givenConcert(LocalDate startDate, LocalDate endDate, Artist artist) {
        Concert concert = Concert.builder()
                .title("Vaundy ASIA ARENA TOUR")
                .venue("인스파이어 아레나")
                .category(ConcertCategory.J_POP_ARTIST)
                .artist(artist)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        ReflectionTestUtils.setField(concert, "id", CONCERT_ID);
        when(concertRepository.findById(CONCERT_ID)).thenReturn(Optional.of(concert));
        return concert;
    }

    private void givenNoConcert() {
        when(concertRepository.findById(CONCERT_ID)).thenReturn(Optional.empty());
    }

    private static void assertNotFound(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONCERT_NOT_FOUND));
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

    // ---------- past-detail ----------

    @Test
    void 지난_공연_상세는_공연명_아티스트_공연장_날짜_공연시간을_돌려준다() {
        Concert concert = givenConcert(TODAY.minusDays(30), TODAY.minusDays(29));
        ReflectionTestUtils.setField(concert, "showtime", "토요일(17:00), 일요일(16:00)");

        ConcertPastDetailDto dto = concertService.getPastDetail(CONCERT_ID, null);

        assertThat(dto.getConcertId()).isEqualTo(CONCERT_ID);
        assertThat(dto.getTitle()).isEqualTo("Vaundy ASIA ARENA TOUR");
        assertThat(dto.getArtistName()).isEqualTo("Vaundy");
        assertThat(dto.getVenue()).isEqualTo("인스파이어 아레나");
        assertThat(dto.getStartDate()).isEqualTo(TODAY.minusDays(30));
        assertThat(dto.getEndDate()).isEqualTo(TODAY.minusDays(29));
        // 공연 시간은 KOPIS 원문 그대로 내려간다 (가공하지 않음)
        assertThat(dto.getShowtime()).isEqualTo("토요일(17:00), 일요일(16:00)");
    }

    @Test
    void 공연시간을_못_가져온_지난_공연도_조회되고_공연시간은_null이다() {
        givenConcert(TODAY.minusDays(30), TODAY.minusDays(29));

        ConcertPastDetailDto dto = concertService.getPastDetail(CONCERT_ID, null);

        assertThat(dto.getTitle()).isEqualTo("Vaundy ASIA ARENA TOUR");
        assertThat(dto.getShowtime()).isNull();
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

    // ---------- upcoming-detail ----------

    @Test
    void 예정_공연_상세는_공연_정보_D_day_예매처_공연장_정보를_돌려준다() {
        Concert concert = givenConcert(TODAY.plusDays(10), TODAY.plusDays(11));
        List<TicketVendorInfo> vendors = List.of(
                new TicketVendorInfo("인터파크", "http://a.example/1"),
                new TicketVendorInfo("멜론티켓", "http://b.example/2"));
        ReflectionTestUtils.setField(concert, "ticketVendors", vendors);
        ReflectionTestUtils.setField(concert, "showtime", "토요일(17:00), 일요일(16:00)");
        ReflectionTestUtils.setField(concert, "venueAddress", "인천광역시 중구 공항문화로 127 (운서동)");
        ReflectionTestUtils.setField(concert, "venueLatitude", 37.4655301);
        ReflectionTestUtils.setField(concert, "venueLongitude", 126.3891177);
        ReflectionTestUtils.setField(concert, "venueCapacity", 14483);

        ConcertUpcomingDetailDto dto = concertService.getUpcomingDetail(CONCERT_ID, null);

        assertThat(dto.getConcertId()).isEqualTo(CONCERT_ID);
        assertThat(dto.getTitle()).isEqualTo("Vaundy ASIA ARENA TOUR");
        assertThat(dto.getArtistName()).isEqualTo("Vaundy");
        assertThat(dto.getVenue()).isEqualTo("인스파이어 아레나");
        assertThat(dto.getStartDate()).isEqualTo(TODAY.plusDays(10));
        assertThat(dto.getEndDate()).isEqualTo(TODAY.plusDays(11));
        assertThat(dto.getDDay()).isEqualTo(10);
        // 공연 시간은 KOPIS 원문 그대로 내려간다 (가공하지 않음)
        assertThat(dto.getShowtime()).isEqualTo("토요일(17:00), 일요일(16:00)");
        assertThat(dto.getTicketVendors()).containsExactlyElementsOf(vendors);
        assertThat(dto.getVenueAddress()).isEqualTo("인천광역시 중구 공항문화로 127 (운서동)");
        assertThat(dto.getVenueLatitude()).isEqualTo(37.4655301);
        assertThat(dto.getVenueLongitude()).isEqualTo(126.3891177);
        assertThat(dto.getVenueCapacity()).isEqualTo(14483);
    }

    @Test
    void 어드민_입력_기능이_생기기_전이라_선예매와_자리배치도는_null이다() {
        givenConcert(TODAY.plusDays(10), TODAY.plusDays(11));

        ConcertUpcomingDetailDto dto = concertService.getUpcomingDetail(CONCERT_ID, null);

        assertThat(dto.getPresaleAvailable()).isNull();
        assertThat(dto.getPresaleDate()).isNull();
        assertThat(dto.getGeneralSaleDate()).isNull();
        assertThat(dto.getSeatingChartImageUrl()).isNull();
    }

    @Test
    void 예매처와_공연장_정보를_못_가져온_공연도_조회되고_해당_필드는_null이다() {
        givenConcert(TODAY.plusDays(10), TODAY.plusDays(11));

        ConcertUpcomingDetailDto dto = concertService.getUpcomingDetail(CONCERT_ID, null);

        assertThat(dto.getTitle()).isEqualTo("Vaundy ASIA ARENA TOUR");
        assertThat(dto.getShowtime()).isNull();
        assertThat(dto.getTicketVendors()).isNull();
        assertThat(dto.getVenueAddress()).isNull();
        assertThat(dto.getVenueCapacity()).isNull();
    }

    @Test
    void 공연_당일의_D_day는_0이다() {
        givenConcert(TODAY, TODAY);

        assertThat(concertService.getUpcomingDetail(CONCERT_ID, null).getDDay()).isZero();
    }

    @Test
    void 이미_시작했지만_아직_안_끝난_공연은_조회되고_D_day는_음수다() {
        givenConcert(TODAY.minusDays(1), TODAY.plusDays(1));

        ConcertUpcomingDetailDto dto = concertService.getUpcomingDetail(CONCERT_ID, null);

        assertThat(dto.getDDay()).isEqualTo(-1);
    }

    @Test
    void 날짜가_미정인_공연은_예정_상세로_조회되고_D_day는_null이다() {
        givenConcert(null, null);

        ConcertUpcomingDetailDto dto = concertService.getUpcomingDetail(CONCERT_ID, null);

        assertThat(dto.getTitle()).isEqualTo("Vaundy ASIA ARENA TOUR");
        assertThat(dto.getDDay()).isNull();
    }

    @Test
    void 아티스트가_없는_공연도_예정_상세로_조회된다() {
        givenConcert(TODAY.plusDays(10), TODAY.plusDays(11), null);

        ConcertUpcomingDetailDto dto = concertService.getUpcomingDetail(CONCERT_ID, null);

        assertThat(dto.getArtistId()).isNull();
        assertThat(dto.getArtistName()).isNull();
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
}