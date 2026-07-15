package com.loop.loop_backend.Chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

@Getter
@Schema(description = "1:1 채팅 시작 요청 DTO")
public class StartDirectChatRequestDto {

    @NotNull
    @Positive
    @Schema(description = "채팅 상대 유저 ID")
    private Long targetUserId;
}
