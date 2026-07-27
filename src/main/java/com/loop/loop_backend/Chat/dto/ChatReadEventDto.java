package com.loop.loop_backend.Chat.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ChatReadEventDto(
        Type type,
        Long roomId,
        Long readerId,
        LocalDateTime readAt
) {
    public enum Type {
        READ
    }
}
