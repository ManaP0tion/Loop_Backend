package com.loop.loop_backend.Chat.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;

@Getter
@Builder(toBuilder = true)
@Jacksonized
public class ChatMessageDto {

    public enum MessageType {
        ENTER, TALK, LEAVE
    }

    private final MessageType type;
    private final Long roomId;
    private final Long senderId;
    private final String content;
    private final LocalDateTime createdAt;
}
