package com.loop.loop_backend.Chat.dto;

import com.loop.loop_backend.Chat.domain.ChatRoom;
import com.loop.loop_backend.Chat.domain.ChatRoomType;
import com.loop.loop_backend.Chat.domain.Message;
import com.loop.loop_backend.User.domain.Gender;
import com.loop.loop_backend.User.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "채팅방 응답 DTO")
public class ChatRoomResponseDto {

    @Schema(description = "채팅방 ID")
    private final Long id;

    @Schema(description = "채팅방 이름")
    private final String name;

    @Schema(description = "채팅방 유형 (DIRECT, GROUP)")
    private final ChatRoomType type;

    @Schema(description = "생성일시")
    private final LocalDateTime createdAt;

    @Schema(description = "상대방 닉네임")
    private final String otherUserNickname;

    @Schema(description = "상대방 프로필 이미지 URL")
    private final String otherUserProfileImageUrl;

    @Schema(description = "상대방 성별")
    private final Gender otherUserGender;

    @Schema(description = "상대방 생년월일")
    private final LocalDate otherUserBirthDate;

    @Schema(description = "마지막 메시지 내용")
    private final String lastMessageContent;

    @Schema(description = "마지막 메시지 시각")
    private final LocalDateTime lastMessageAt;

    @Schema(description = "안읽은 메시지 수")
    private final long unreadCount;

    @Schema(description = "상대방과의 관계 상태 (탈퇴/차단/신고)")
    private final ChatOtherUserRelationDto otherUserRelation;

    public static ChatRoomResponseDto from(ChatRoom room) {
        return ChatRoomResponseDto.builder()
                .id(room.getId())
                .name(room.getName())
                .type(room.getType())
                .createdAt(room.getCreatedAt())
                .build();
    }

    public static ChatRoomResponseDto from(ChatRoom room, User otherUser, ChatOtherUserRelationDto relation) {
        ChatRoomResponseDtoBuilder builder = ChatRoomResponseDto.builder()
                .id(room.getId())
                .name(room.getName())
                .type(room.getType())
                .createdAt(room.getCreatedAt())
                .otherUserRelation(relation);

        if (otherUser != null) {
            builder.otherUserNickname(otherUser.getNickname())
                    .otherUserProfileImageUrl(otherUser.getProfileImageUrl())
                    .otherUserGender(otherUser.getGender())
                    .otherUserBirthDate(otherUser.getBirthDate());
        }

        return builder.build();
    }

    public static ChatRoomResponseDto forList(ChatRoom room, User otherUser, Message lastMessage, long unreadCount) {
        ChatRoomResponseDtoBuilder builder = ChatRoomResponseDto.builder()
                .id(room.getId())
                .name(room.getName())
                .type(room.getType())
                .createdAt(room.getCreatedAt())
                .unreadCount(unreadCount);

        if (otherUser != null) {
            builder.otherUserNickname(otherUser.getNickname())
                    .otherUserProfileImageUrl(otherUser.getProfileImageUrl())
                    .otherUserGender(otherUser.getGender())
                    .otherUserBirthDate(otherUser.getBirthDate());
        }

        if (lastMessage != null) {
            builder.lastMessageContent(lastMessage.getContent())
                    .lastMessageAt(lastMessage.getCreatedAt());
        }

        return builder.build();
    }
}
