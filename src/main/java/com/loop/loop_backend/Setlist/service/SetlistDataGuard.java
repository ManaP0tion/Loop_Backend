package com.loop.loop_backend.Setlist.service;

import com.loop.loop_backend.Setlist.repository.SetlistRepository;
import com.loop.loop_backend.Setlist.repository.SetlistVoteRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 셋리스트·투표가 붙은 출연 정보는 지우거나 헤드라이너를 해제할 수 없다(409).
 * DB는 출연 정보 삭제 시 셋리스트·투표를 함께 지우므로(cascade), 지우기 전에 여기서 막는다.
 */
@Component
@RequiredArgsConstructor
public class SetlistDataGuard {

    private final SetlistRepository setlistRepository;
    private final SetlistVoteRepository voteRepository;

    public void requireNone(Long performanceId) {
        if (setlistRepository.existsByPerformanceId(performanceId) || voteRepository.existsByPerformanceId(performanceId)) {
            throw new BusinessException(ErrorCode.PERFORMANCE_HAS_SETLIST_DATA);
        }
    }

    /** 공연의 DAY > afterDay인 출연 정보 대상. afterDay = 0이면 공연 전체. */
    public void requireNoneAfterDay(Long concertId, int afterDay) {
        if (setlistRepository.existsByPerformanceConcertIdAndPerformanceDayGreaterThan(concertId, afterDay)
                || voteRepository.existsByPerformanceConcertIdAndPerformanceDayGreaterThan(concertId, afterDay)) {
            throw new BusinessException(ErrorCode.PERFORMANCE_HAS_SETLIST_DATA);
        }
    }
}
