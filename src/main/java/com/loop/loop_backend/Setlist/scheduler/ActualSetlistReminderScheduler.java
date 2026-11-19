package com.loop.loop_backend.Setlist.scheduler;

import com.loop.loop_backend.Setlist.service.ActualSetlistReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActualSetlistReminderScheduler {

    private static final ZoneId ZONE_KST = ZoneId.of("Asia/Seoul");

    private final ActualSetlistReminderService reminderService;

    // 매일 오전 9시 10분(KST) - 어제 끝난 공연의 실제 셋리스트 등록 리마인드.
    // 09:00 KOPIS 싱크와 겹치지 않게 10분 뒤로 둔다(스케줄러 스레드가 하나라 싱크가 길어지면 그만큼 늦게 나간다).
    @Scheduled(cron = "0 10 9 * * *", zone = "Asia/Seoul")
    public void remindActualSetlist() {
        log.info("Scheduled actual setlist reminder triggered");
        reminderService.remind(LocalDate.now(ZONE_KST));
    }
}