package com.loop.loop_backend.Chat.scheduler;

import com.loop.loop_backend.Chat.dto.UnreadChatDigestRow;
import com.loop.loop_backend.Chat.repository.MessageRepository;
import com.loop.loop_backend.Mail.dto.UnreadChatRoomSummary;
import com.loop.loop_backend.Mail.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class UnreadChatMailScheduler {

    private static final ZoneId ZONE_KST = ZoneId.of("Asia/Seoul");

    private final MessageRepository messageRepository;
    private final MailService mailService;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    @Transactional(readOnly = true)
    public void sendDailyUnreadDigest() {
        LocalDateTime cutoff = LocalDate.now(ZONE_KST).atStartOfDay();
        List<UnreadChatDigestRow> rows = messageRepository.findDailyUnreadDigest(cutoff);
        if (rows.isEmpty()) {
            return;
        }

        Long currentRecipientId = null;
        String currentEmail = null;
        String currentNickname = null;
        List<UnreadChatRoomSummary> buffer = new ArrayList<>();
        int mailsSent = 0;

        for (UnreadChatDigestRow row : rows) {
            if (currentRecipientId != null && !currentRecipientId.equals(row.recipientId())) {
                mailService.sendUnreadChatNotification(currentEmail, currentNickname, buffer);
                mailsSent++;
                buffer = new ArrayList<>();
            }
            currentRecipientId = row.recipientId();
            currentEmail = row.recipientEmail();
            currentNickname = row.recipientNickname();
            buffer.add(new UnreadChatRoomSummary(
                    row.roomId(),
                    row.partnerNickname(),
                    row.unreadCount(),
                    row.lastMessageAt()
            ));
        }
        if (!buffer.isEmpty()) {
            mailService.sendUnreadChatNotification(currentEmail, currentNickname, buffer);
            mailsSent++;
        }

        log.info("일일 미확인 채팅 알림 발송 완료 (recipients={}, cutoff={})", mailsSent, cutoff);
    }
}
