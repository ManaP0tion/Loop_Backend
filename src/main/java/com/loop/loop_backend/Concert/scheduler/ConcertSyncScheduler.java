package com.loop.loop_backend.Concert.scheduler;

import com.loop.loop_backend.Concert.kopis.KopisSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConcertSyncScheduler {

    private final KopisSyncService kopisSyncService;

    // 매일 새벽 2시 실행
    @Scheduled(cron = "0 0 2 * * *")
    public void syncConcerts() {
        log.info("Scheduled KOPIS concert sync triggered");
        kopisSyncService.syncAll();
    }
}
