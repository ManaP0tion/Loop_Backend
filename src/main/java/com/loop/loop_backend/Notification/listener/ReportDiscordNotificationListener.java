package com.loop.loop_backend.Notification.listener;

import com.loop.loop_backend.Notification.service.DiscordNotificationService;
import com.loop.loop_backend.Report.event.ReportCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ReportDiscordNotificationListener {

    private final DiscordNotificationService discordNotificationService;

    @Value("${discord.webhook.report}")
    private String webhookUrl;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReportCreated(ReportCreatedEvent event) {
        String content = """
                **새 신고 접수** (ID: %d)
                신고자: %s
                대상: %s
                사유: %s
                상세: %s
                """
                .formatted(event.reportId(), event.reporterNickname(), event.targetNickname(),
                        event.reason() == null ? "-" : event.reason(),
                        event.detail() == null ? "-" : event.detail());

        discordNotificationService.send(webhookUrl, content);
    }
}