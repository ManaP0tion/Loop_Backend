package com.loop.loop_backend.Chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "STOMP 채팅 에러 이벤트 (/sub/chat/errors/{myUserId} 구독)")
public record ChatErrorDto(
        @Schema(description = "에러 코드 (ErrorCode enum name)") String code,
        @Schema(description = "표시용 메시지") String message,
        @Schema(description = "관련 방 ID (있으면)") Long roomId
) {}
