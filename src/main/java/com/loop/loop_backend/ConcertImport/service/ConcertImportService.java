package com.loop.loop_backend.ConcertImport.service;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
import com.loop.loop_backend.Concert.domain.ticket.ConcertGeneralSale;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Concert.repository.ticket.ConcertGeneralSaleRepository;
import com.loop.loop_backend.ConcertImport.domain.ConcertImport;
import com.loop.loop_backend.ConcertImport.domain.ImportStatus;
import com.loop.loop_backend.ConcertImport.kopis.KopisClient;
import com.loop.loop_backend.ConcertImport.kopis.KopisShowtimes;
import com.loop.loop_backend.ConcertImport.kopis.KopisTicketVendors;
import com.loop.loop_backend.ConcertImport.repository.ConcertImportRepository;
import com.loop.loop_backend.Venue.domain.Venue;
import com.loop.loop_backend.Venue.service.VenueService;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

/**
 * 관리자 검토 → 승인/거절 처리. 승인 시 ConcertImport → Concert(운영 데이터) 변환.
 */
@Service
@RequiredArgsConstructor
public class ConcertImportService {

    private final ConcertImportRepository importRepository;
    private final ConcertRepository concertRepository;
    private final ConcertGeneralSaleRepository generalSaleRepository;
    private final KopisClient kopisClient;
    private final VenueService venueService;

    /**
     * 승인: KOPIS 값을 기본값으로 채워 비공개 공연을 만든다(요청 값 없음).
     * 관리자는 이후 공연 수정 API(PATCH /api/admin/concerts/{id})로 고치고 공개하며, 포스터는 POST /{id}/image로 바꾼다.
     */
    @Transactional
    public Concert approve(Long importId) {
        ConcertImport imp = findPending(importId);

        // 가격/공연시간/예매처 목록은 KOPIS 상세에만 있어 승인 시점에 1회 조회한다(실패해도 null로 진행).
        KopisClient.KopisDetail detail = kopisClient.getPerformanceDetail(imp.getKopisId());
        // 공연장 정보(주소/좌표/수용인원)는 상세 응답의 시설·홀 ID로 시설 API를 이어서 조회한다.
        // 상세 조회가 실패했으면 시설 ID도 없어 호출 없이 전부 null이 된다.
        KopisClient.KopisFacility facility = kopisClient.getFacility(detail.facilityId(), detail.hallId());
        // 공연장 관리(AD-02)에 같은 KOPIS 시설·홀이 있으면 연결하고, 없으면 KOPIS 값으로 만들어 연결한다.
        // KOPIS 조회에 실패해 공연장을 만들 수 없으면 연결 없이 승인한다 - 어드민 공연 수정 API에서 공연장을 골라 연결한다(예정).
        Venue venue = venueService.findOrCreateFromKopis(detail.facilityId(), detail.hallId(), facility);

        Concert concert = Concert.builder()
                .artist(imp.getMatchedArtist())
                .kopisId(imp.getKopisId())
                .title(imp.getTitle())
                .posterUrl(imp.getPosterUrl())
                .venue(imp.getVenue())
                .startDate(imp.getStartDate())
                .endDate(imp.getEndDate())
                .price(detail.price())
                .showtime(detail.showtime())
                .ticketVendors(detail.ticketVendors())
                // 어드민 화면/DTO가 아직 단일 ticketUrl을 쓰므로 예매처 목록의 첫 링크를 함께 채운다.
                .ticketUrl(detail.ticketUrl())
                .venueAddress(facility.address())
                .venueLatitude(facility.latitude())
                .venueLongitude(facility.longitude())
                .venueCapacity(facility.capacity())
                .linkedVenue(venue)
                .category(imp.getSuggestedCategory())
                .build();

        // DAY별 공연 시각 기본값: KOPIS 공연 시간 안내를 날짜의 요일에 맞춰 채운다(못 정한 DAY는 비움). 기간을 모르면 건너뛴다.
        List<LocalTime> startTimes = KopisShowtimes.startTimesByDay(detail.showtime(), concert.getStartDate(), concert.getEndDate());
        if (!startTimes.isEmpty()) concert.replaceShowtimes(startTimes);
        concertRepository.save(concert);

        // 일반예매 기본값: KOPIS 예매처를 한 블록에 모두 넣는다. 예매 일시는 KOPIS에 없어 비워 두고 관리자가 입력한다.
        List<TicketVendorInfo> vendors = KopisTicketVendors.toGeneralSaleVendors(detail.ticketVendors());
        if (!vendors.isEmpty()) {
            generalSaleRepository.save(ConcertGeneralSale.builder().concert(concert).vendors(vendors).build());
        }

        imp.markApproved(concert.getId());
        return concert;
    }

    @Transactional
    public void reject(Long importId, String reason) {
        findPending(importId).markRejected(reason);
    }

    private ConcertImport findPending(Long importId) {
        ConcertImport imp = importRepository.findById(importId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_IMPORT_NOT_FOUND));
        if (imp.getStatus() != ImportStatus.PENDING) {
            throw new BusinessException(ErrorCode.IMPORT_ALREADY_PROCESSED);
        }
        return imp;
    }

}
