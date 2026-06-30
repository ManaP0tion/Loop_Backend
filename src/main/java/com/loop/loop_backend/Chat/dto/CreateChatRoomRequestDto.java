package com.loop.loop_backend.Chat.dto;

import com.loop.loop_backend.Chat.domain.ChatRoomType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

@Getter
@Schema(description = "채팅방 생성 요청 DTO")
public class CreateChatRoomRequestDto {

    @NotNull
    @Positive(message = "postId는 1 이상이어야 합니다.")
    @Schema(description = "동행 게시글 ID")
    private Long postId;

    @NotNull
    @Positive(message = "hostUserId는 1 이상이어야 합니다.")
    @Schema(description = "방장(동행 게시글 작성자) 유저 ID")
    private Long hostUserId;

    @NotNull
    @Positive(message = "applicantUserId는 1 이상이어야 합니다.")
    @Schema(description = "동행 신청자 유저 ID")
    private Long applicantUserId;

    @Schema(description = "채팅방 이름 (생략 시 자동 생성)")
    private String name;

    @Schema(description = "채팅방 유형", defaultValue = "GROUP")
    private ChatRoomType type = ChatRoomType.GROUP;
}
