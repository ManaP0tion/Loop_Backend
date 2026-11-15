package com.loop.loop_backend.Concert.scheduler;

import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.CompanionPost.dto.ConcertReminderRow;
import com.loop.loop_backend.CompanionPost.repository.CompanionPostRepository;
import com.loop.loop_backend.Mail.dto.ConcertReminderSummary;
import com.loop.loop_backend.Mail.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConcertReminderScheduler {

    private static final ZoneId ZONE_KST = ZoneId.of("Asia/Seoul");

    private final CompanionPostRepository companionPostRepository;
    private final MailService mailService;

    // 매일 오전 10시(KST) - 내일 공연 관람 예정자에게 리마인더 발송
    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Seoul")
    @Transactional(readOnly = true)
    public void sendDailyConcertReminder() {
        LocalDate tomorrow = LocalDate.now(ZONE_KST).plusDays(1);

        List<ConcertReminderRow> rows = companionPostRepository.findConcertReminderRows(
                tomorrow,               // DAY1
                tomorrow.minusDays(1),  // DAY2
                tomorrow.minusDays(2),  // DAY3
                tomorrow.minusDays(3)   // DAY4
        );
        if (rows.isEmpty()) {
            return;
        }

        Long currentRecipientId = null;
        String currentEmail = null;
        String currentNickname = null;
        List<ConcertReminderSummary> buffer = new ArrayList<>();
        int mailsSent = 0;

        for (ConcertReminderRow row : rows) {
            if (currentRecipientId != null && !currentRecipientId.equals(row.recipientId())) {
                mailService.sendConcertReminderNotification(currentEmail, currentNickname, buffer);
                mailsSent++;
                buffer = new ArrayList<>();
            }
            currentRecipientId = row.recipientId();
            currentEmail = row.recipientEmail();
            currentNickname = row.recipientNickname();
            buffer.add(new ConcertReminderSummary(
                    row.concertId(),
                    row.concertTitle(),
                    row.venue(),
                    watchDate(row.startDate(), row.watchDay())
            ));
        }
        if (!buffer.isEmpty()) {
            mailService.sendConcertReminderNotification(currentEmail, currentNickname, buffer);
            mailsSent++;
        }

        log.info("공연 하루전 리마인더 발송 완료 (recipients={}, tomorrow={})", mailsSent, tomorrow);
    }

    private LocalDate watchDate(LocalDate startDate, WatchDay watchDay) {
        return startDate.plusDays(watchDay.ordinal());
    }
}
