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
        ENTER, TALK, IMAGE, LEAVE, SYSTEM_LEAVE, SYSTEM_WITHDRAWN, SYSTEM_REJOIN
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

    // 한 번에 보낸 이미지 묶음 id. 같은 값끼리 묶어서 보여준다. 서버만 채운다(클라이언트가 보낸 값은 무시)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private final String imageGroupId;
}
