package com.loop.loop_backend.ConcertImport.service;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.domain.ConcertShowtime;
import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
import com.loop.loop_backend.Concert.domain.ticket.ConcertGeneralSale;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Concert.repository.ticket.ConcertGeneralSaleRepository;
import com.loop.loop_backend.ConcertImport.domain.ConcertImport;
import com.loop.loop_backend.ConcertImport.domain.ImportStatus;
import com.loop.loop_backend.ConcertImport.kopis.KopisClient;
import com.loop.loop_backend.ConcertImport.repository.ConcertImportRepository;
import com.loop.loop_backend.Venue.domain.Venue;
import com.loop.loop_backend.Venue.service.VenueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 승인 요구사항: 승인하면 KOPIS 값을 기본값으로 채운 비공개 공연이 만들어진다(요청 값 없음).
// - KOPIS 상세(가격·공연 시간·예매처)와 공연장 정보가 채워지고, 공연장 관리의 공연장이 연결된다.
// - 일반예매 1건에 KOPIS 예매처가 모두 들어간다(놀유니버스 → NOL). 예매 일시는 KOPIS에 없어 비어 있다. 예매처가 없으면 만들지 않는다.
// - DAY별 공연 시각은 KOPIS 공연 시간 안내를 날짜의 요일에 맞춰 채운다.
// - KOPIS 조회가 실패해도 승인은 된다.
@ExtendWith(MockitoExtension.class)
class ConcertImportServiceTest {

    @Mock ConcertImportRepository importRepository;
    @Mock ConcertRepository concertRepository;
    @Mock ConcertGeneralSaleRepository generalSaleRepository;
    @Mock KopisClient kopisClient;
    @Mock VenueService venueService;
    @InjectMocks ConcertImportService service;

    private ConcertImport pendingImport;

    private static final KopisClient.KopisFacility NO_FACILITY =
            new KopisClient.KopisFacility(null, null, null, null, null, null);

    @BeforeEach
    void setUp() {
        // 실제 KOPIS 공연(Vaundy): 2026-09-19(토) ~ 09-20(일), 공연 시간 "토요일(17:00), 일요일(16:00)"
        pendingImport = ConcertImport.builder()
                .kopisId("PF287093")
                .title("Vaundy ASIA ARENA TOUR HORO IN SEOUL")
                .venue("인스파이어 엔터테인먼트 리조트 (아레나)")
                .startDate(LocalDate.of(2026, 9, 19))
                .endDate(LocalDate.of(2026, 9, 20))
                .suggestedCategory(ConcertCategory.J_POP_ARTIST)
                .status(ImportStatus.PENDING)
                .build();
        when(importRepository.findById(1L)).thenReturn(Optional.of(pendingImport));
    }

    private void givenKopisDetail(String showtime, List<TicketVendorInfo> vendors) {
        when(kopisClient.getPerformanceDetail("PF287093")).thenReturn(new KopisClient.KopisDetail(
                "스탠딩석 165,000원", showtime, vendors, "FC003670", "FC003670-01"));
        when(kopisClient.getFacility("FC003670", "FC003670-01")).thenReturn(NO_FACILITY);
    }

    private void givenKopisFailed() {
        when(kopisClient.getPerformanceDetail("PF287093"))
                .thenReturn(new KopisClient.KopisDetail(null, null, null, null, null));
        when(kopisClient.getFacility(null, null)).thenReturn(NO_FACILITY);
    }

    private Concert approveAndGetSavedConcert() {
        service.approve(1L);
        ArgumentCaptor<Concert> saved = ArgumentCaptor.forClass(Concert.class);
        verify(concertRepository).save(saved.capture());
        return saved.getValue();
    }

    // ---------- KOPIS 상세·공연장 ----------

    @Test
    void 승인하면_KOPIS_상세와_공연장_정보가_Concert에_채워진다() {
        List<TicketVendorInfo> vendors = List.of(
                new TicketVendorInfo("인터파크", "http://a.example/1"),
                new TicketVendorInfo("멜론티켓", "http://b.example/2"));
        when(kopisClient.getPerformanceDetail("PF287093")).thenReturn(new KopisClient.KopisDetail(
                "스탠딩석 165,000원", "토요일(17:00), 일요일(16:00)", vendors, "FC003670", "FC003670-01"));
        // 시설 조회에는 상세 응답의 시설ID/홀ID가 그대로 넘어가야 한다
        when(kopisClient.getFacility("FC003670", "FC003670-01")).thenReturn(new KopisClient.KopisFacility(
                "인스파이어 엔터테인먼트 리조트", "아레나", "인천광역시 중구 공항문화로 127 (운서동)", 37.4655301, 126.3891177, 14483));

        Concert concert = approveAndGetSavedConcert();

        assertThat(concert.getPrice()).isEqualTo("스탠딩석 165,000원");
        assertThat(concert.getShowtime()).isEqualTo("토요일(17:00), 일요일(16:00)");
        assertThat(concert.getTicketVendors()).containsExactlyElementsOf(vendors);
        assertThat(concert.getVenueAddress()).isEqualTo("인천광역시 중구 공항문화로 127 (운서동)");
        assertThat(concert.getVenueLatitude()).isEqualTo(37.4655301);
        assertThat(concert.getVenueLongitude()).isEqualTo(126.3891177);
        assertThat(concert.getVenueCapacity()).isEqualTo(14483);
    }

    @Test
    void 단일_예매처_링크_ticketUrl에는_예매처_목록의_첫_링크가_들어간다() {
        givenKopisDetail(null, List.of(new TicketVendorInfo("인터파크", "http://a.example/1"),
                new TicketVendorInfo("멜론티켓", "http://b.example/2")));

        assertThat(approveAndGetSavedConcert().getTicketUrl()).isEqualTo("http://a.example/1");
    }

    @Test
    void 승인하면_KOPIS_시설_홀에_해당하는_공연장이_공연에_연결된다() {
        when(kopisClient.getPerformanceDetail("PF287093"))
                .thenReturn(new KopisClient.KopisDetail(null, null, List.of(), "FC003670", "FC003670-01"));
        KopisClient.KopisFacility facility = new KopisClient.KopisFacility(
                "인스파이어 엔터테인먼트 리조트", "아레나", "인천광역시 중구 공항문화로 127 (운서동)", null, null, 14483);
        when(kopisClient.getFacility("FC003670", "FC003670-01")).thenReturn(facility);
        Venue arena = Venue.builder().name("인스파이어 아레나").address("인천광역시 중구")
                .kopisFacilityId("FC003670").kopisHallId("FC003670-01").build();
        when(venueService.findOrCreateFromKopis("FC003670", "FC003670-01", facility)).thenReturn(arena);

        assertThat(approveAndGetSavedConcert().getLinkedVenue()).isSameAs(arena);
    }

    // ---------- 승인 결과 ----------

    @Test
    void 승인한_공연은_비공개이고_검토_큐의_값이_그대로_들어간다() {
        givenKopisDetail(null, List.of());

        Concert concert = approveAndGetSavedConcert();

        assertThat(concert.isPublished()).isFalse();
        assertThat(concert.getTitle()).isEqualTo("Vaundy ASIA ARENA TOUR HORO IN SEOUL");
        assertThat(concert.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 19));
        assertThat(concert.getCategory()).isEqualTo(ConcertCategory.J_POP_ARTIST);
        assertThat(pendingImport.getStatus()).isEqualTo(ImportStatus.APPROVED);
    }

    // ---------- 일반예매 기본값 ----------

    @Test
    void KOPIS_예매처가_일반예매_한_건에_모두_들어가고_놀유니버스는_NOL이_된다() {
        givenKopisDetail(null, List.of(
                new TicketVendorInfo("놀유니버스", "https://nol"),
                new TicketVendorInfo("멜론티켓", "https://melon"),
                new TicketVendorInfo("티켓링크", "https://link")));

        service.approve(1L);

        ArgumentCaptor<ConcertGeneralSale> sale = ArgumentCaptor.forClass(ConcertGeneralSale.class);
        verify(generalSaleRepository).save(sale.capture());
        assertThat(sale.getValue().getOpensAt()).isNull(); // 예매 일시는 KOPIS에 없다
        assertThat(sale.getValue().getVendors()).containsExactly(
                new TicketVendorInfo("NOL", "https://nol"),
                new TicketVendorInfo("멜론티켓", "https://melon"),
                new TicketVendorInfo("티켓링크", "https://link"));
    }

    @Test
    void KOPIS_예매처가_없으면_일반예매를_만들지_않는다() {
        givenKopisDetail(null, List.of());

        service.approve(1L);

        verify(generalSaleRepository, never()).save(any());
    }

    // ---------- DAY별 공연 시각 기본값 ----------

    @Test
    void KOPIS_공연_시간_안내로_DAY별_시각이_채워진다() {
        givenKopisDetail("토요일(17:00), 일요일(16:00)", List.of());

        assertThat(approveAndGetSavedConcert().getShowtimes())
                .extracting(ConcertShowtime::getDate, ConcertShowtime::getStartTime)
                .containsExactly(
                        tuple(LocalDate.of(2026, 9, 19), LocalTime.of(17, 0)),
                        tuple(LocalDate.of(2026, 9, 20), LocalTime.of(16, 0)));
    }

    // ---------- KOPIS 조회 실패 ----------

    @Test
    void KOPIS_조회가_실패해도_승인은_되고_KOPIS에서_오는_값만_비어_있다() {
        givenKopisFailed();

        Concert concert = approveAndGetSavedConcert();

        assertThat(concert.getTitle()).isEqualTo("Vaundy ASIA ARENA TOUR HORO IN SEOUL");
        assertThat(concert.getPrice()).isNull();
        assertThat(concert.getTicketVendors()).isNull();
        assertThat(concert.getVenueAddress()).isNull();
        assertThat(concert.getLinkedVenue()).isNull();
        // 공연 시간 안내가 없으니 DAY는 있되 시각은 모두 미정
        assertThat(concert.getShowtimes()).extracting(ConcertShowtime::getStartTime).containsOnlyNulls();
        verify(generalSaleRepository, never()).save(any());
        assertThat(pendingImport.getStatus()).isEqualTo(ImportStatus.APPROVED);
    }
}