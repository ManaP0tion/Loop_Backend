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

    @Schema(description = "연결된 공연 ID (원본 동행글이 있으면 그 공연 ID, 없으면 null)")
    private final Long concertId;

    @Schema(description = "연결된 상대방 동행글 ID (원본이 삭제되었거나 최초 생성 시 미지정이면 null)")
    private final Long otherCompanionId;

    @Schema(description = "상대방 유저 ID (탈퇴/삭제된 유저도 참여자 이력으로 확인. 차단/신고 대상 식별용)")
    private final Long otherUserId;

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
        return baseBuilder(room).build();
    }

    // startDirectChat처럼 트랜잭션 밖에서 조립하는 경우: 엔티티를 훑지 않고 이미 조회된 스칼라 값(summary)만 사용
    public static ChatRoomResponseDto fromSummary(ChatRoomSummaryDto summary, Long otherUserId, User otherUser,
                                                   ChatOtherUserRelationDto relation) {
        ChatRoomResponseDtoBuilder builder = ChatRoomResponseDto.builder()
                .id(summary.id())
                .name(summary.name())
                .type(summary.type())
                .createdAt(summary.createdAt())
                .otherCompanionId(summary.postId())
                .concertId(summary.concertId())
                .otherUserId(otherUserId)
                .otherUserRelation(relation);

        if (otherUser != null) {
            builder.otherUserNickname(otherUser.getNickname())
                    .otherUserProfileImageUrl(otherUser.getProfileImageUrl())
                    .otherUserGender(otherUser.getGender())
                    .otherUserBirthDate(otherUser.getBirthDate());
        }

        return builder.build();
    }

    public static ChatRoomResponseDto forList(ChatRoom room, Long otherUserId, User otherUser, Message lastMessage, long unreadCount) {
        ChatRoomResponseDtoBuilder builder = baseBuilder(room)
                .otherUserId(otherUserId)
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

    private static ChatRoomResponseDtoBuilder baseBuilder(ChatRoom room) {
        ChatRoomResponseDtoBuilder builder = ChatRoomResponseDto.builder()
                .id(room.getId())
                .name(room.getName())
                .type(room.getType())
                .createdAt(room.getCreatedAt());

        if (room.getPost() != null) {
            builder.otherCompanionId(room.getPost().getId());
            if (room.getPost().getConcert() != null) {
                builder.concertId(room.getPost().getConcert().getId());
            }
        }
        return builder;
    }
}
