package com.loop.loop_backend.Notification.listener;

import com.loop.loop_backend.CompanionPost.event.CompanionCreatedEvent;
import com.loop.loop_backend.Notification.service.DiscordNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;

/**
 * 동행 프로필이 새로 생기면 동행 채널로 한 통 알린다(현황 파악용, 수정은 알리지 않는다).
 * 저장이 커밋된 뒤에만 보내고, 디스코드 실패는 프로필 생성에 영향을 주지 않는다.
 */
@Component
@RequiredArgsConstructor
public class CompanionDiscordNotificationListener {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final DiscordNotificationService discordNotificationService;

    @Value("${discord.webhook.companion}")
    private String webhookUrl;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCompanionCreated(CompanionCreatedEvent event) {
        discordNotificationService.send(webhookUrl, message(event));
    }

    static String message(CompanionCreatedEvent event) {
        String text = event.messageToCompanion();
        return """
                **새 동행 프로필** (ID: %d)
                공연: %s
                관람일: %s
                작성자: %s
                하고 싶은 말: %s"""
                .formatted(event.companionId(), event.concertTitle(), watchDay(event), event.authorNickname(),
                        text == null || text.isBlank() ? "-" : text);
    }

    /** DAY1 (2026.11.03). 공연 날짜가 미정이면 DAY만 */
    private static String watchDay(CompanionCreatedEvent event) {
        if (event.watchDate() == null) return event.watchDay().name();
        return event.watchDay().name() + " (" + event.watchDate().format(DATE) + ")";
    }
}