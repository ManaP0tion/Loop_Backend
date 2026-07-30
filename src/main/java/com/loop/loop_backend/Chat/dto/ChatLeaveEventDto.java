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
        LEAVE,
        // 나갔던(LEFT) 참여자가 다시 채팅을 시작해 ACTIVE로 복귀. leaverId = 복귀한 유저, leftAt = null.
        // 상대 클라이언트가 "상대가 나감" 상태를 해제하고 입력창을 다시 열도록 알리는 신호.
        REJOIN
    }
}
