package com.loop.loop_backend.Chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;

@Getter
@Builder(toBuilder = true)
@Jacksonized
public class ChatMessageDto {

    public enum MessageType {
        ENTER, TALK, LEAVE, SYSTEM_LEAVE, SYSTEM_WITHDRAWN, SYSTEM_REJOIN
    }

    @NotNull
    private final MessageType type;

    @NotNull
    private final Long roomId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private final Long senderId;

    @NotBlank
    @Size(max = 1000)
    private final String content;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private final LocalDateTime createdAt;

    @JsonProperty(value = "isRead", access = JsonProperty.Access.READ_ONLY)
    private final Boolean isRead;
}
