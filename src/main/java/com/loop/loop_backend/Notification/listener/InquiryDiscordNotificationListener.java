package com.loop.loop_backend.Notification.listener;

import com.loop.loop_backend.Inquiry.event.InquiryCreatedEvent;
import com.loop.loop_backend.Notification.service.DiscordNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class InquiryDiscordNotificationListener {

    private final DiscordNotificationService discordNotificationService;

    @Value("${discord.webhook.inquiry}")
    private String webhookUrl;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleInquiryCreated(InquiryCreatedEvent event) {
        String content = """
                **새 문의 접수** (ID: %d)
                작성자: %s
                답변 이메일: %s
                유형: %s
                제목: %s
                내용: %s
                """
                .formatted(event.inquiryId(), event.userNickname(), event.email(), event.type().getLabel(), event.title(), event.content());

        discordNotificationService.send(webhookUrl, content);
    }
}