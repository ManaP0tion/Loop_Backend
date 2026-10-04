package com.loop.loop_backend.Concert.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.dto.admin.AdminConcertCreateRequest;
import com.loop.loop_backend.Concert.dto.admin.AdminConcertDetailResponse;
import com.loop.loop_backend.Concert.dto.admin.AdminConcertUpdateRequest;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Concert.repository.ticket.ConcertGeneralSaleRepository;
import com.loop.loop_backend.Concert.repository.ticket.ConcertPresaleRepository;
import com.loop.loop_backend.Venue.domain.Venue;
import com.loop.loop_backend.Venue.repository.VenueRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 공연 등록·수정(AD-01). 규칙 검사는 Concert의 도메인 메서드가 맡고, 여기서는 요청을 순서대로 반영한다.
 * 규칙 위반(IllegalArgumentException)은 400, 트랜잭션이 롤백되어 일부만 반영되는 일이 없다.
 */
@Service
@RequiredArgsConstructor
public class AdminConcertService {

    private final ConcertRepository concertRepository;
    private final ArtistRepository artistRepository;
    private final VenueRepository venueRepository;
    private final ConcertPresaleRepository presaleRepository;
    private final ConcertGeneralSaleRepository generalSaleRepository;

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
        if (req.expectedSongCount() != null) concert.changeExpectedSongCount(req.expectedSongCount());
        concert.changeLodgingUrl(req.lodgingUrl());
        if (req.lodgingVisible() != null) concert.changeLodgingVisible(req.lodgingVisible());
        if (req.productCodes() != null) concert.replaceProductCodes(req.productCodes());
        concert.validateState();
        // 방금 만든 공연이라 예매 정보가 없다
        return AdminConcertDetailResponse.from(concertRepository.save(concert), List.of(), List.of());
    }

    /**
     * 부분 수정 - null은 변경 없음. 반영 순서가 의미 있다:
     * 유형 먼저(페스티벌로 바뀌면 아티스트·예상 곡 수를 비우고, 그 뒤 들어온 값을 페스티벌 규칙으로 검사),
     * 기간 다음 시각(시각 개수는 바뀐 기간 기준), 공개 여부는 마지막에 반영하고 전체 상태를 검사한다.
     */
    @Transactional
    public AdminConcertDetailResponse update(Long id, AdminConcertUpdateRequest req) {
        Concert concert = findConcert(id);

        // 수정 화면은 원래 유형을 그대로 보내므로 바뀔 때만 유형 규칙을 적용한다(국내 유형인 기존 공연도 다른 필드는 고칠 수 있게).
        if (req.category() != null && req.category() != concert.getCategory()) {
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
        if (req.expectedSongCount() != null) concert.changeExpectedSongCount(req.expectedSongCount());
        if (req.lodgingUrl() != null) concert.changeLodgingUrl(req.lodgingUrl());
        if (req.lodgingVisible() != null) concert.changeLodgingVisible(req.lodgingVisible());
        if (req.productCodes() != null) concert.replaceProductCodes(req.productCodes());
        if (req.published() != null) concert.changePublished(req.published(), LocalDateTime.now());

        concert.validateState();
        return detail(concert);
    }

    private AdminConcertDetailResponse detail(Concert concert) {
        return AdminConcertDetailResponse.from(concert,
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