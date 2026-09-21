package com.loop.loop_backend.Concert.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.domain.ConcertImport;
import com.loop.loop_backend.Concert.domain.ImportStatus;
import com.loop.loop_backend.Concert.kopis.KopisClient;
import com.loop.loop_backend.Concert.repository.ConcertImportRepository;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 관리자 검토 → 승인/거절 처리. 승인 시 ConcertImport → Concert(운영 데이터) 변환.
 */
@Service
@RequiredArgsConstructor
public class ConcertImportService {

    private final ConcertImportRepository importRepository;
    private final ConcertRepository concertRepository;
    private final ArtistRepository artistRepository;
    private final KopisClient kopisClient;

    /**
     * 승인: import 원본값을 기본으로 하되, 관리자가 넘긴 수정값(non-null)이 있으면 우선 적용해 Concert 생성.
     * 포스터 이미지 교체가 필요하면 승인 후 기존 POST /api/admin/concerts/{id}/image 를 재사용한다.
     */
    @Transactional
    public Concert approve(Long importId, ApproveCommand cmd) {
        ConcertImport imp = findPending(importId);

        Artist artist = (cmd.artistId() != null)
                ? artistRepository.findById(cmd.artistId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.ARTIST_NOT_FOUND))
                : imp.getMatchedArtist();

        ConcertCategory category = firstNonNull(cmd.category(), imp.getSuggestedCategory());

        // 가격/예매처URL/공연시간은 KOPIS 상세에만 있어 승인 시점에 1회 조회한다(실패해도 null로 진행).
        KopisClient.KopisDetail detail = kopisClient.getPerformanceDetail(imp.getKopisId());

        Concert concert = Concert.builder()
                .artist(artist)
                .kopisId(imp.getKopisId())
                .title(firstNonNull(cmd.title(), imp.getTitle()))
                .posterUrl(firstNonNull(cmd.posterUrl(), imp.getPosterUrl()))
                .venue(firstNonNull(cmd.venue(), imp.getVenue()))
                .startDate(firstNonNull(cmd.startDate(), imp.getStartDate()))
                .endDate(firstNonNull(cmd.endDate(), imp.getEndDate()))
                .price(detail.price())
                .ticketUrl(detail.ticketUrl())
                .showtime(detail.showtime())
                .category(category)
                .build();
        concertRepository.save(concert);

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

    private static <T> T firstNonNull(T a, T b) {
        return a != null ? a : b;
    }

    /** 승인 시 관리자 수정값. 모두 nullable — null이면 import 원본값을 그대로 사용. */
    public record ApproveCommand(Long artistId, String title, String posterUrl, String venue,
                                 LocalDate startDate, LocalDate endDate, ConcertCategory category) {}
}
