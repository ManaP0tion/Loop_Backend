package com.loop.loop_backend.Concert.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
import com.loop.loop_backend.Concert.domain.ticket.ConcertGeneralSale;
import com.loop.loop_backend.Concert.domain.ticket.ConcertPresale;
import com.loop.loop_backend.Concert.dto.admin.AdminConcertCreateRequest;
import com.loop.loop_backend.Concert.dto.admin.AdminConcertDetailResponse;
import com.loop.loop_backend.Concert.dto.admin.AdminConcertRow;
import com.loop.loop_backend.Concert.dto.admin.AdminConcertUpdateRequest;
import com.loop.loop_backend.Concert.dto.admin.TicketSaleRequest;
import com.loop.loop_backend.Concert.repository.AdminConcertRepository;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Concert.repository.ticket.ConcertGeneralSaleRepository;
import com.loop.loop_backend.Concert.repository.ticket.ConcertPresaleRepository;
import com.loop.loop_backend.Performance.domain.Performance;
import com.loop.loop_backend.Performance.repository.PerformanceRepository;
import com.loop.loop_backend.Performance.service.PerformanceSync;
import com.loop.loop_backend.Venue.domain.Venue;
import com.loop.loop_backend.Venue.repository.VenueRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 관리자 공연 등록·수정(AD-01). 규칙 검사는 Concert의 도메인 메서드가 맡고, 여기서는 요청을 순서대로 반영한다.
 * 규칙 위반(IllegalArgumentException)은 400, 트랜잭션이 롤백되어 일부만 반영되는 일이 없다.
 */
@Service
@RequiredArgsConstructor
public class AdminConcertService {

    private final ConcertRepository concertRepository;
    private final AdminConcertRepository adminConcertRepository;
    private final ArtistRepository artistRepository;
    private final VenueRepository venueRepository;
    private final ConcertPresaleRepository presaleRepository;
    private final ConcertGeneralSaleRepository generalSaleRepository;
    private final PerformanceRepository performanceRepository;
    private final PerformanceSync performanceSync;

    /**
     * 등록된 공연 목록. 예매 등록 여부는 페이지의 공연들을 한 번에 확인한다 - 공연마다 확인하면 공연 수만큼 쿼리가 늘어난다(N+1).
     * 공연장·아티스트는 목록 쿼리에서 함께 가져온다.
     */
    @Transactional(readOnly = true)
    public Page<AdminConcertRow> search(String q, ConcertCategory category, Boolean published, Pageable pageable) {
        String keyword = (q == null || q.isBlank()) ? null : "%" + q.trim().toLowerCase() + "%";
        Page<Concert> page = adminConcertRepository.search(keyword, category, published, pageable);
        Set<Long> scheduled = ticketScheduledConcertIds(page.getContent());
        return page.map(c -> AdminConcertRow.from(c, scheduled.contains(c.getId())));
    }

    /** 예매 일시가 입력된 선예매·일반예매가 하나라도 있는 공연 id. 일시 없는 블록은 사용자 화면에 보이지 않아 등록으로 치지 않는다. */
    private Set<Long> ticketScheduledConcertIds(List<Concert> concerts) {
        if (concerts.isEmpty()) return Set.of();
        List<Long> ids = concerts.stream().map(Concert::getId).toList();
        Set<Long> scheduled = new HashSet<>(presaleRepository.findConcertIdsWithOpensAt(ids));
        scheduled.addAll(generalSaleRepository.findConcertIdsWithOpensAt(ids));
        return scheduled;
    }

    @Transactional(readOnly = true)
    public AdminConcertDetailResponse get(Long id) {
        return detail(findConcert(id));
    }

    /** 직접 등록 - 항상 비공개로 만든다(포스터는 생성 후 업로드, 공개는 수정에서). */
    @Transactional
    public AdminConcertDetailResponse create(AdminConcertCreateRequest req) {
        Concert concert = Concert.builder()
                .title(req.title().trim())
                .category(req.category())
                .build();
        concert.changeCategory(req.category()); // 유형 필수 검사
        if (req.titleAliases() != null) concert.replaceTitleAliases(req.titleAliases());
        concert.changePeriod(req.startDate(), req.endDate());
        if (req.showtimes() != null) concert.replaceShowtimes(req.showtimes());
        if (req.venueId() != null) concert.changeVenue(findVenue(req.venueId()));
        if (req.artistIds() != null) concert.changeArtist(findSingleArtist(req.artistIds()));
        applyLodgingToVenue(concert, req.lodgingVisible(), req.lodgingUrl());
        concert.changeOfficialSiteUrl(req.officialSiteUrl());
        concert.changeInstagramUrl(req.instagramUrl());
        concert.changeXUrl(req.xUrl());
        concert.validateState();
        concertRepository.save(concert);
        // 출연 정보·예매 블록은 공연 id가 있어야 저장할 수 있어 공연을 먼저 저장한다
        performanceSync.sync(concert, false, req.expectedSongCount());
        if (req.presales() != null) replacePresales(concert, req.presales());
        if (req.generalSales() != null) replaceGeneralSales(concert, req.generalSales());
        return detail(concert);
    }

    /**
     * 부분 수정 - null은 변경 없음. 반영 순서가 의미 있다:
     * 유형 먼저(페스티벌로 바뀌면 아티스트를 비우고, 그 뒤 들어온 값을 페스티벌 규칙으로 검사),
     * 기간 다음 시각(시각 개수는 바뀐 기간 기준), 공개 여부는 마지막에 반영하고 전체 상태를 검사한다.
     */
    @Transactional
    public AdminConcertDetailResponse update(Long id, AdminConcertUpdateRequest req) {
        Concert concert = findConcert(id);

        // 수정 화면은 원래 유형을 그대로 보내므로 바뀔 때만 유형 규칙(페스티벌이면 아티스트 비움)을 적용한다.
        boolean categoryChanged = req.category() != null && req.category() != concert.getCategory();
        if (categoryChanged) {
            concert.changeCategory(req.category());
        }
        if (req.title() != null) concert.rename(req.title());
        if (req.titleAliases() != null) concert.replaceTitleAliases(req.titleAliases());
        if (req.startDate() != null || req.endDate() != null) {
            concert.changePeriod(
                    req.startDate() != null ? req.startDate() : concert.getStartDate(),
                    req.endDate() != null ? req.endDate() : concert.getEndDate());
        }
        if (req.showtimes() != null) concert.replaceShowtimes(req.showtimes());
        if (req.venueId() != null) concert.changeVenue(findVenue(req.venueId()));
        if (req.artistIds() != null) concert.changeArtist(findSingleArtist(req.artistIds()));
        // 공연장 변경(venueId) 다음에 둔다 - 같은 요청에서 공연장을 바꾸면 새 공연장에 저장되게
        applyLodgingToVenue(concert, req.lodgingVisible(), req.lodgingUrl());
        if (req.officialSiteUrl() != null) concert.changeOfficialSiteUrl(req.officialSiteUrl());
        if (req.instagramUrl() != null) concert.changeInstagramUrl(req.instagramUrl());
        if (req.xUrl() != null) concert.changeXUrl(req.xUrl());
        if (req.published() != null) concert.changePublished(req.published(), LocalDateTime.now());
        // 예매 정보도 같은 저장 버튼으로 함께 온다 - 실패하면 위 수정과 함께 롤백된다
        if (req.presales() != null) replacePresales(concert, req.presales());
        if (req.generalSales() != null) replaceGeneralSales(concert, req.generalSales());

        concert.validateState();
        performanceSync.sync(concert, categoryChanged, req.expectedSongCount());
        return detail(concert);
    }

    /** 선예매를 통째로 교체한다(기존 블록 삭제 후 새로 저장, 블록 id는 바뀐다). 예매처 규칙은 엔티티가 검사한다. */
    private void replacePresales(Concert concert, List<TicketSaleRequest> requests) {
        presaleRepository.deleteAll(presaleRepository.findByConcert_Id(concert.getId()));
        presaleRepository.saveAll(requests.stream()
                .map(r -> ConcertPresale.builder().concert(concert).opensAt(r.opensAt()).vendors(vendorsOf(r)).build())
                .toList());
    }

    /** 일반예매를 통째로 교체한다(기존 블록 삭제 후 새로 저장, 블록 id는 바뀐다). */
    private void replaceGeneralSales(Concert concert, List<TicketSaleRequest> requests) {
        generalSaleRepository.deleteAll(generalSaleRepository.findByConcert_Id(concert.getId()));
        generalSaleRepository.saveAll(requests.stream()
                .map(r -> ConcertGeneralSale.builder().concert(concert).opensAt(r.opensAt()).vendors(vendorsOf(r)).build())
                .toList());
    }

    private static List<TicketVendorInfo> vendorsOf(TicketSaleRequest req) {
        if (req.vendors() == null) return List.of();
        return req.vendors().stream()
                .map(v -> new TicketVendorInfo(v.name(), v.url()))
                .toList();
    }

    private AdminConcertDetailResponse detail(Concert concert) {
        Performance solo = concert.isFestival() ? null
                : performanceRepository.findFirstByConcertId(concert.getId()).orElse(null);
        return AdminConcertDetailResponse.from(concert, solo,
                presaleRepository.findByConcert_Id(concert.getId()),
                generalSaleRepository.findByConcert_Id(concert.getId()));
    }

    private Concert findConcert(Long id) {
        return concertRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));
    }

    private Venue findVenue(Long id) {
        return venueRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.VENUE_NOT_FOUND));
    }

    /**
     * 공연 화면에서 받은 숙소 값은 연결된 공연장에 저장한다(같은 공연장의 다른 공연에도 반영 - 숙소는 공연장 단위).
     * 공연장이 없는 공연은 저장할 곳이 없어, 노출을 켜거나 딥링크를 넣으면 400.
     * 바꾸는 게 없는 값(false, "")은 무시한다 - 화면이 기본값을 늘 보내도 공연장 없는 공연을 저장할 수 있게.
     */
    private static void applyLodgingToVenue(Concert concert, Boolean visible, String url) {
        Venue venue = concert.getLinkedVenue();
        if (venue != null) {
            venue.patchLodging(visible, url);
            return;
        }
        if (Boolean.TRUE.equals(visible) || (url != null && !url.isBlank())) {
            throw new IllegalArgumentException("공연장이 없는 공연에는 숙소를 저장할 수 없다 - 공연장을 먼저 연결해야 한다");
        }
    }

    /** 아티스트는 지금 1명까지(@Size(max = 1)로 이미 검사). 빈 목록이면 비운다. */
    private Artist findSingleArtist(List<Long> artistIds) {
        if (artistIds.isEmpty()) return null;
        return artistRepository.findById(artistIds.get(0))
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTIST_NOT_FOUND));
    }
}