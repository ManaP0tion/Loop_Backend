package com.loop.loop_backend.Mail.dto;

import java.time.LocalDateTime;

public record UnreadChatRoomSummary(
        Long roomId,
        String partnerNickname,
        long unreadCount,
        LocalDateTime lastMessageAt
) {
}
