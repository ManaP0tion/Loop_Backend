package com.loop.loop_backend.Chat.dto;

import com.loop.loop_backend.Chat.domain.ChatRoomType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

@Getter
@Schema(description = "채팅방 생성 요청 DTO (요청자 = 신청자, host는 postId로부터 조회)")
public class CreateChatRoomRequestDto {

    @NotNull
    @Positive(message = "postId는 1 이상이어야 합니다.")
    @Schema(description = "동행 게시글 ID")
    private Long postId;

    @Schema(description = "채팅방 이름 (생략 시 자동 생성)")
    private String name;

    @Schema(description = "채팅방 유형", defaultValue = "GROUP")
    private ChatRoomType type = ChatRoomType.GROUP;
}
