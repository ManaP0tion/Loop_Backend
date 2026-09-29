package com.loop.loop_backend.ConcertScrap.service;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.dto.ConcertPeriod;
import com.loop.loop_backend.Concert.dto.ConcertSummaryDto;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.ConcertScrap.domain.ConcertScrap;
import com.loop.loop_backend.ConcertScrap.repository.ConcertScrapRepository;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import com.loop.loop_backend.common.time.ExpiryCutoff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConcertScrapServiceImpl implements ConcertScrapService {

    private final ConcertScrapRepository concertScrapRepository;
    private final ConcertRepository concertRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void scrap(Long userId, Long concertId) {
        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));

        if (concertScrapRepository.existsByUser_IdAndConcert_Id(userId, concertId)) {
            return; // 이미 스크랩한 상태 - 토글 UX라 에러 없이 조용히 성공 처리
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        concertScrapRepository.save(ConcertScrap.builder().user(user).concert(concert).build());
    }

    @Override
    @Transactional
    public void unscrap(Long userId, Long concertId) {
        // 스크랩 안 한 상태(또는 이미 삭제된 콘서트)에서 취소 요청이 와도 토글 UX라 에러 없이 조용히 성공 처리
        concertScrapRepository.findByUser_IdAndConcert_Id(userId, concertId)
                .ifPresent(concertScrapRepository::delete);
    }

    @Override
    public List<ConcertSummaryDto> getMyScraps(Long userId, ConcertPeriod period) {
        // 공연 목록과 같은 만료 기준(오전 10시 리셋)을 써야 공연 탭과 스크랩 탭의 예정/지난 구분이 어긋나지 않는다.
        LocalDate cutoff = ExpiryCutoff.cutoffDate();
        List<Concert> concerts = (period == ConcertPeriod.UPCOMING)
                ? concertScrapRepository.findUpcomingOrUndatedScrappedConcerts(userId, cutoff)
                : concertScrapRepository.findPastScrappedConcerts(userId, cutoff);
        return concerts.stream().map(ConcertSummaryDto::from).toList();
    }
}