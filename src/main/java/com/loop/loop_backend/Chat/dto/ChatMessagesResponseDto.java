package com.loop.loop_backend.Chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Slice;

@Getter
@Builder
@Schema(description = "채팅 메시지 페이지 + 상대방과의 관계 상태")
public class ChatMessagesResponseDto {

    @Schema(description = "연결된 공연 ID (연결된 동행글이 없으면 null)")
    private final Long concertId;

    @Schema(description = "연결된 상대방 동행글 ID (없으면 null)")
    private final Long otherCompanionId;

    @Schema(description = "상대방과의 관계 상태 (탈퇴/차단/신고). DIRECT 방이 아니면 null.")
    private final ChatOtherUserRelationDto otherUserRelation;

    @Schema(description = "메시지 페이지 (최신순)")
    private final Slice<ChatMessageDto> messages;
}
