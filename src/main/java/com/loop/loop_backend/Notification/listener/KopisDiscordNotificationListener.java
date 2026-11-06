package com.loop.loop_backend.Notification.listener;

import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.ConcertImport.event.KopisSyncCompletedEvent;
import com.loop.loop_backend.ConcertImport.event.KopisSyncCompletedEvent.NewImport;
import com.loop.loop_backend.ConcertImport.kopis.SyncTrigger;
import com.loop.loop_backend.Notification.service.DiscordNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 매일 자동 KOPIS 싱크로 검토 대기열에 새 공연이 들어오면 디스코드로 한 통 알린다.
 * - 자동 실행(09:00)만 알린다. 어드민 수동 실행은 실행한 관리자가 바로 대기열을 보므로 알리지 않는다.
 * - 새 건이 없으면 실패가 있어도 보내지 않는다(실패는 서버 로그로 본다).
 * 싱크는 트랜잭션 밖에서 끝나므로 @TransactionalEventListener가 아니라 @EventListener로 받는다(AFTER_COMMIT이면 이벤트가 버려진다).
 */
@Component
@RequiredArgsConstructor
public class KopisDiscordNotificationListener {

    // 디스코드 메시지는 2000자 제한이라 목록 줄 수와 공연명 길이를 제한한다
    static final int MAX_LISTED = 10;
    static final int MAX_TITLE_LENGTH = 80;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final DiscordNotificationService discordNotificationService;

    @Value("${discord.webhook.kopis}")
    private String webhookUrl;

    @Async("notificationExecutor")
    @EventListener
    public void handleSyncCompleted(KopisSyncCompletedEvent event) {
        if (event.trigger() != SyncTrigger.SCHEDULED) return;
        if (event.newImports().isEmpty()) return;
        discordNotificationService.send(webhookUrl, message(event.newImports()));
    }

    static String message(List<NewImport> newImports) {
        String lines = newImports.stream().limit(MAX_LISTED)
                .map(KopisDiscordNotificationListener::line)
                .collect(Collectors.joining("\n"));
        String more = newImports.size() > MAX_LISTED ? "\n외 " + (newImports.size() - MAX_LISTED) + "건" : "";
        return "**KOPIS 신규 수집 " + newImports.size() + "건** (검토 대기)\n" + lines + more;
    }

    /** - [아티스트] 공연명 (기간). 페스티벌은 [페스티벌] */
    private static String line(NewImport imp) {
        return "- " + label(imp) + truncate(imp.title()) + " (" + period(imp.startDate(), imp.endDate()) + ")";
    }

    private static String label(NewImport imp) {
        if (imp.category() == ConcertCategory.FESTIVAL) return "[페스티벌] ";
        if (imp.artistNames().isEmpty()) return "";
        return "[" + String.join(", ", imp.artistNames()) + "] ";
    }

    private static String truncate(String title) {
        return title.length() <= MAX_TITLE_LENGTH ? title : title.substring(0, MAX_TITLE_LENGTH) + "…";
    }

    private static String period(LocalDate start, LocalDate end) {
        if (start == null) return "날짜 미정";
        if (end == null || end.equals(start)) return start.format(DATE);
        return start.format(DATE) + "~" + end.format(DATE);
    }
}