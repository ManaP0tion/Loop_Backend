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
        ChatRoomResponseDtoBuilder builder = baseBuilder(room);
        // createRoom()의 응답 경로는 호출자가 그 글의 주인일 수 없다는 게 상위 검증
        // (SELF_CHAT_NOT_ALLOWED)으로 보장돼 있어서 안전하다. 다만 joinRoom()(GROUP 참여)은
        // 이런 검증이 없어서, host 본인이 자기 GROUP 방에 join하면 여기서도 자기 글 id가
        // "상대방 글"인 것처럼 내려갈 수 있다 — GROUP은 현재 프로덕션 미사용이라 후순위로 남겨둠.
        if (room.getPost() != null) {
            builder.otherCompanionId(room.getPost().getId());
        }
        return builder.build();
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

    public static ChatRoomResponseDto forList(ChatRoom room, Long otherUserId, User otherUser, Message lastMessage,
                                               long unreadCount, Long viewerId) {
        ChatRoomResponseDtoBuilder builder = baseBuilder(room)
                .otherUserId(otherUserId)
                .unreadCount(unreadCount);

        // otherCompanionId는 "상대방 동행글 ID"인데, room.post는 항상 그 글을 올린 host의 글이다.
        // 이 목록을 보는 사람(viewerId)이 바로 그 host 본인이면 "상대방 글"이 아니라 "내 글"이므로
        // 내려주면 안 되고(그동안은 항상 내려줘서 host 본인 글이 상대방 글인 것처럼 노출되던 버그),
        // viewerId가 host가 아닐 때(=신청자 입장일 때)만 채운다.
        if (room.getPost() != null && !room.getPost().getUser().getId().equals(viewerId)) {
            builder.otherCompanionId(room.getPost().getId());
        }

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

    // otherCompanionId는 여기서 채우지 않는다 — "누가 보는지"에 따라 값이 달라져야 해서
    // (host 본인이 보면 안 채움) 호출부(from/forList)에서 각자의 기준으로 채우게 한다.
    private static ChatRoomResponseDtoBuilder baseBuilder(ChatRoom room) {
        ChatRoomResponseDtoBuilder builder = ChatRoomResponseDto.builder()
                .id(room.getId())
                .name(room.getName())
                .type(room.getType())
                .createdAt(room.getCreatedAt());

        // room.post.concert가 아니라 방 자체에 스냅샷으로 저장된 concertId를 사용 — post가 나중에
        // 삭제돼도(회원 탈퇴 등) 이 값은 안 끊긴다.
        if (room.getConcertId() != null) {
            builder.concertId(room.getConcertId());
        }
        return builder;
    }
}
