package com.loop.loop_backend.Setlist.service;

import com.loop.loop_backend.Setlist.repository.SetlistRepository;
import com.loop.loop_backend.Setlist.repository.SetlistVoteRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;

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

    /** 여러 출연 정보를 한꺼번에 지울 때(공연 수정). 하나라도 셋리스트·투표가 있으면 409. */
    public void requireNone(Collection<Long> performanceIds) {
        if (performanceIds.isEmpty()) return;
        if (setlistRepository.existsByPerformanceIdIn(performanceIds) || voteRepository.existsByPerformanceIdIn(performanceIds)) {
            throw new BusinessException(ErrorCode.PERFORMANCE_HAS_SETLIST_DATA);
        }
    }
}
