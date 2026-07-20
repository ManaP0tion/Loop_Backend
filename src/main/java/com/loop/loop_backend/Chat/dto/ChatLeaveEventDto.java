package com.loop.loop_backend.Chat.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ChatLeaveEventDto(
        Type type,
        Long roomId,
        Long leaverId,
        LocalDateTime leftAt
) {
    public enum Type {
        LEAVE
    }
}
