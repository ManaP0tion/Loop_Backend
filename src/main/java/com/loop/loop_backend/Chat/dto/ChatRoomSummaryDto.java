package com.loop.loop_backend.Chat.dto;

import com.loop.loop_backend.Chat.domain.ChatRoomType;

import java.time.LocalDateTime;

// startDirectChat처럼 트랜잭션 밖에서 응답을 조립해야 하는 경우를 위한 순수 값 프로젝션.
// 엔티티를 들고 나가지 않으므로 이후 필드 접근에서 LazyInitializationException이 날 수 없다.
public record ChatRoomSummaryDto(
        Long id,
        String name,
        ChatRoomType type,
        LocalDateTime createdAt,
        Long postId,
        Long concertId
) {
}
