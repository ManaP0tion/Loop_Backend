package com.loop.loop_backend.Concert.service;

import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.domain.ConcertImport;
import com.loop.loop_backend.Concert.domain.ImportStatus;
import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
import com.loop.loop_backend.Concert.kopis.KopisClient;
import com.loop.loop_backend.Concert.repository.ConcertImportRepository;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 승인하면 KOPIS 상세/공연장 정보가 Concert에 채워지는지, 조회가 실패해도 승인 자체는 되는지 검증한다.
@ExtendWith(MockitoExtension.class)
class ConcertImportServiceTest {

    private static final ConcertImportService.ApproveCommand NO_OVERRIDES =
            new ConcertImportService.ApproveCommand(null, null, null, null, null, null, null);

    @Mock ConcertImportRepository importRepository;
    @Mock ConcertRepository concertRepository;
    @Mock ArtistRepository artistRepository;
    @Mock KopisClient kopisClient;
    @InjectMocks ConcertImportService service;

    private ConcertImport pendingImport;

    @BeforeEach
    void setUp() {
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

    private Concert approveAndGetSavedConcert() {
        service.approve(1L, NO_OVERRIDES);
        ArgumentCaptor<Concert> saved = ArgumentCaptor.forClass(Concert.class);
        verify(concertRepository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void 승인하면_KOPIS_상세와_공연장_정보가_Concert에_채워진다() {
        List<TicketVendorInfo> vendors = List.of(
                new TicketVendorInfo("인터파크", "http://a.example/1"),
                new TicketVendorInfo("멜론티켓", "http://b.example/2"));
        when(kopisClient.getPerformanceDetail("PF287093")).thenReturn(new KopisClient.KopisDetail(
                "스탠딩석 165,000원", "토요일(17:00), 일요일(16:00)", vendors, "FC003670", "FC003670-01"));
        // 시설 조회에는 상세 응답의 시설ID/홀ID가 그대로 넘어가야 한다
        when(kopisClient.getFacility("FC003670", "FC003670-01")).thenReturn(new KopisClient.KopisFacility(
                "인천광역시 중구 공항문화로 127 (운서동)", 37.4655301, 126.3891177, 14483));

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
        when(kopisClient.getPerformanceDetail("PF287093")).thenReturn(new KopisClient.KopisDetail(
                null, null, List.of(new TicketVendorInfo("인터파크", "http://a.example/1"),
                new TicketVendorInfo("멜론티켓", "http://b.example/2")), "FC003670", "FC003670-01"));
        when(kopisClient.getFacility("FC003670", "FC003670-01"))
                .thenReturn(new KopisClient.KopisFacility(null, null, null, null));

        assertThat(approveAndGetSavedConcert().getTicketUrl()).isEqualTo("http://a.example/1");
    }

    @Test
    void KOPIS_조회가_실패해도_승인은_되고_상세와_공연장_필드는_null이다() {
        when(kopisClient.getPerformanceDetail("PF287093"))
                .thenReturn(new KopisClient.KopisDetail(null, null, null, null, null));
        when(kopisClient.getFacility(null, null)).thenReturn(new KopisClient.KopisFacility(null, null, null, null));

        Concert concert = approveAndGetSavedConcert();

        assertThat(concert.getTitle()).isEqualTo("Vaundy ASIA ARENA TOUR HORO IN SEOUL");
        assertThat(concert.getPrice()).isNull();
        assertThat(concert.getShowtime()).isNull();
        assertThat(concert.getTicketVendors()).isNull();
        assertThat(concert.getTicketUrl()).isNull();
        assertThat(concert.getVenueAddress()).isNull();
        assertThat(concert.getVenueCapacity()).isNull();
        assertThat(pendingImport.getStatus()).isEqualTo(ImportStatus.APPROVED);
    }

    @Test
    void 승인하면_후보는_APPROVED가_된다() {
        when(kopisClient.getPerformanceDetail("PF287093"))
                .thenReturn(new KopisClient.KopisDetail(null, null, List.of(), "FC003670", "FC003670-01"));
        when(kopisClient.getFacility("FC003670", "FC003670-01"))
                .thenReturn(new KopisClient.KopisFacility(null, null, null, null));
        when(concertRepository.save(any(Concert.class))).thenAnswer(inv -> inv.getArgument(0));

        service.approve(1L, NO_OVERRIDES);

        assertThat(pendingImport.getStatus()).isEqualTo(ImportStatus.APPROVED);
    }
}