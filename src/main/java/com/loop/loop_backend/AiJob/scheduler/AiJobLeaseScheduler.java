package com.loop.loop_backend.AiJob.scheduler;

import com.loop.loop_backend.AiJob.service.AiJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * lease가 지난 AI 작업 정리(워커가 꺼졌거나 잠자기·죽음).
 * claim은 5초마다 들어오므로 거기 붙이지 않고 1분 주기로 따로 돈다 - 작업이 20~60초라 최대 1분 늦게 정리돼도 괜찮다.
 */
@Component
@RequiredArgsConstructor
public class AiJobLeaseScheduler {

    private final AiJobService aiJobService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void expireLeases() {
        aiJobService.expireLeases();
    }
}