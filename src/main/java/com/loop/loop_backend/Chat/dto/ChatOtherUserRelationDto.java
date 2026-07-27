package com.loop.loop_backend.Chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "채팅 상대방과의 관계 상태 (1:1 DIRECT 방 기준)")
public class ChatOtherUserRelationDto {

    @Schema(description = "상대방이 탈퇴한 회원인지 여부")
    private final boolean otherUserWithdrawn;

    @Schema(description = "내가 상대방을 차단했는지 여부")
    private final boolean blockedByMe;

    @Schema(description = "상대방이 나를 차단했는지 여부")
    private final boolean blockedMe;

    @Schema(description = "내가 상대방을 신고했는지 여부")
    private final boolean reportedByMe;

    public boolean isChatDisabled() {
        return otherUserWithdrawn || blockedByMe || blockedMe;
    }
}
