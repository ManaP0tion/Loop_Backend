package com.loop.loop_backend.Concert.scheduler;

import com.loop.loop_backend.Concert.service.NewConcertNotificationService;
import com.loop.loop_backend.Mail.dto.NewConcertSummary;
import com.loop.loop_backend.Mail.service.MailService;
import com.loop.loop_backend.User.repository.MailRecipient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 새 공연 알림(NO.47): 대상 선정은 서비스 트랜잭션에서 끝내고(커밋), 메일은 그 뒤 여기서 보낸다.
 * 같은 빈 안에서 부르면 @Transactional이 적용되지 않아 빈을 나눴다. 메일은 트랜잭션 밖이라 발송 동안 DB 연결을 잡지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewConcertNotificationScheduler {

    private final NewConcertNotificationService newConcertNotificationService;
    private final MailService mailService;

    // 10분마다 - 공개 후 1시간이 지난 공연은 최대 10분 늦게 나간다.
    // ponytail: 수신자 전원을 스케줄러 스레드에서 순차 발송. 수천 명 단위면 다른 예약 작업이 밀리므로 별도 스레드·SES 대량 발송으로
    @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Seoul")
    public void notifyNewConcerts() {
        // publishedAt(AdminConcertService)과 같은 서버 기본 시간대로 비교한다
        notifyNewConcerts(LocalDateTime.now());
    }

    void notifyNewConcerts(LocalDateTime now) {
        List<NewConcertSummary> concerts = newConcertNotificationService.claimDueConcerts(now);
        if (concerts.isEmpty()) return;

        List<MailRecipient> recipients = newConcertNotificationService.findRecipients();
        int failed = 0;
        for (MailRecipient r : recipients) {
            // 발송 실패는 MailService가 로그로 남기고 넘어가지만, 템플릿 오류 등으로 한 명이 막혀도 나머지는 보낸다
            try {
                mailService.sendNewConcertNotification(r.email(), r.nickname(), concerts);
            } catch (Exception e) {
                failed++;
                log.error("새 공연 알림 메일 발송 실패 (to={})", r.email(), e);
            }
        }
        log.info("새 공연 알림 발송 완료 (concerts={}, recipients={}, failed={})",
                concerts.stream().map(NewConcertSummary::concertId).toList(), recipients.size(), failed);
    }
}
