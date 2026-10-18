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
import com.loop.loop_backend.Setlist.service.SetlistDataGuard;
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
    private final SetlistDataGuard setlistDataGuard;

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
        concert.changeCategory(req.category()); // 선택 가능한 유형인지 검사
        if (req.titleAliases() != null) concert.replaceTitleAliases(req.titleAliases());
        concert.changePeriod(req.startDate(), req.endDate());
        if (req.showtimes() != null) concert.replaceShowtimes(req.showtimes());
        if (req.venueId() != null) concert.changeVenue(findVenue(req.venueId()));
        if (req.artistIds() != null) concert.changeArtist(findSingleArtist(req.artistIds()));
        concert.changeLodgingUrl(req.lodgingUrl());
        if (req.lodgingVisible() != null) concert.changeLodgingVisible(req.lodgingVisible());
        concert.changeOfficialSiteUrl(req.officialSiteUrl());
        concert.validateState();
        concertRepository.save(concert);
        // 출연 정보·예매 블록은 공연 id가 있어야 저장할 수 있어 공연을 먼저 저장한다
        syncPerformances(concert, false, req.expectedSongCount());
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

        // 수정 화면은 원래 유형을 그대로 보내므로 바뀔 때만 유형 규칙을 적용한다(국내 유형인 기존 공연도 다른 필드는 고칠 수 있게).
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
        if (req.lodgingUrl() != null) concert.changeLodgingUrl(req.lodgingUrl());
        if (req.lodgingVisible() != null) concert.changeLodgingVisible(req.lodgingVisible());
        if (req.officialSiteUrl() != null) concert.changeOfficialSiteUrl(req.officialSiteUrl());
        if (req.published() != null) concert.changePublished(req.published(), LocalDateTime.now());
        // 예매 정보도 같은 저장 버튼으로 함께 온다 - 실패하면 위 수정과 함께 롤백된다
        if (req.presales() != null) replacePresales(concert, req.presales());
        if (req.generalSales() != null) replaceGeneralSales(concert, req.generalSales());

        concert.validateState();
        syncPerformances(concert, categoryChanged, req.expectedSongCount());
        return detail(concert);
    }

    /**
     * 출연 정보 정리·동기화. Concert는 출연 정보를 모르므로 여기서 한다. 바뀐 게 없으면 지워지거나 바뀌는 행이 없다.
     * - 유형이 바뀌면 기존 출연 정보를 모두 지운다(페스티벌 라인업과 단독 공연 출연 정보는 서로 다른 데이터).
     * - 페스티벌: 기간이 줄어 범위를 벗어난 DAY를 지운다. 예상 곡 수는 라인업에서 헤드라이너별로 정한다.
     * - 단독 공연: Concert.artist에 맞춰 출연 정보 1건을 만들거나 아티스트를 바꾸거나 지운다. 예상 곡 수는 여기에 둔다.
     * 셋리스트·투표가 붙은 출연 정보를 지우게 되면 409 - 트랜잭션이 롤백되어 공연 수정도 반영되지 않는다.
     */
    private void syncPerformances(Concert concert, boolean categoryChanged, Integer expectedSongCount) {
        Long concertId = concert.getId();
        if (concert.isFestival()) {
            if (expectedSongCount != null) {
                throw new IllegalArgumentException("페스티벌 예상 곡 수는 라인업에서 헤드라이너별로 정한다");
            }
            deletePerformancesAfterDay(concertId, categoryChanged ? 0 : Performance.dayCount(concert));
            return;
        }
        if (categoryChanged || concert.getArtist() == null) {
            deletePerformancesAfterDay(concertId, 0);
        }
        if (concert.getArtist() == null) {
            if (expectedSongCount != null) {
                throw new IllegalArgumentException("아티스트 없이 예상 곡 수를 정할 수 없다");
            }
            return;
        }
        // 아티스트를 바꿔도 출연 정보는 그대로 두고 아티스트만 바꾼다 - 이미 받은 투표·셋리스트는 남는다(기존 동작 유지)
        Performance solo = performanceRepository.findFirstByConcertId(concertId)
                .orElseGet(() -> performanceRepository.save(Performance.solo(concert, concert.getArtist())));
        solo.changeArtist(concert.getArtist());
        if (expectedSongCount != null) solo.changeExpectedSongCount(expectedSongCount);
    }

    /** DAY > afterDay인 출연 정보를 지운다(0이면 전부). 셋리스트·투표가 붙어 있으면 409. */
    private void deletePerformancesAfterDay(Long concertId, int afterDay) {
        setlistDataGuard.requireNoneAfterDay(concertId, afterDay);
        performanceRepository.deleteByConcertIdAndDayGreaterThan(concertId, afterDay);
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

    /** 아티스트는 지금 1명까지(@Size(max = 1)로 이미 검사). 빈 목록이면 비운다. */
    private Artist findSingleArtist(List<Long> artistIds) {
        if (artistIds.isEmpty()) return null;
        return artistRepository.findById(artistIds.get(0))
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTIST_NOT_FOUND));
    }
}