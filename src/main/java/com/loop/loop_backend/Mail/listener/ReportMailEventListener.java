package com.loop.loop_backend.Mail.listener;

import com.loop.loop_backend.Mail.service.MailService;
import com.loop.loop_backend.Report.event.ReportCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ReportMailEventListener {

    private final MailService mailService;

    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReportCreated(ReportCreatedEvent event) {
        mailService.sendReportNotification(
                event.reportId(),
                event.reporterNickname(),
                event.targetNickname(),
                event.reason(),
                event.detail(),
                event.imageUrls());
    }
}