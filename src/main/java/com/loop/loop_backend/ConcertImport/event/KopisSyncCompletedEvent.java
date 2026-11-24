package com.loop.loop_backend.ConcertImport.event;

import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.ConcertImport.kopis.SyncTrigger;

import java.time.LocalDate;
import java.util.List;

/**
 * KOPIS 싱크 1회가 끝났을 때 발행한다. 실행 경로와 상관없이 항상 발행하고, 알림 여부는 받는 쪽이 정한다.
 * 싱크는 트랜잭션 밖에서 돌기 때문에 @TransactionalEventListener가 아니라 @EventListener로 받아야 한다.
 *
 * @param newImports 이번 싱크로 검토 대기열에 새로 들어온 공연(공연 단위, 아티스트가 여럿이어도 1건)
 * @param failed     처리 중 예외가 난 공연 수
 */
public record KopisSyncCompletedEvent(SyncTrigger trigger, List<NewImport> newImports, int failed) {

    /**
     * @param artistNames 매칭된 아티스트 이름. 페스티벌은 빈 목록
     */
    public record NewImport(String title, LocalDate startDate, LocalDate endDate,
                            ConcertCategory category, List<String> artistNames) {
    }
}