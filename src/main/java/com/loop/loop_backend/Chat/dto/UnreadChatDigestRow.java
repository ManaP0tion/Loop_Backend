package com.loop.loop_backend.Chat.dto;

import java.time.LocalDateTime;

public record UnreadChatDigestRow(
        Long recipientId,
        String recipientEmail,
        String recipientNickname,
        Long roomId,
        String partnerNickname,
        Long unreadCount,
        LocalDateTime lastMessageAt
) {
}
