package com.loop.loop_backend.Chat.service;

import com.loop.loop_backend.Chat.domain.ChatParticipant;
import com.loop.loop_backend.Chat.domain.ChatRoom;
import com.loop.loop_backend.Chat.domain.ChatRoomType;
import com.loop.loop_backend.Chat.domain.Message;
import com.loop.loop_backend.Chat.domain.MessageType;
import com.loop.loop_backend.Chat.domain.ParticipantRole;
import com.loop.loop_backend.Chat.domain.ParticipantStatus;
import com.loop.loop_backend.Chat.dto.ChatLeaveEventDto;
import com.loop.loop_backend.Chat.dto.ChatMessageDto;
import com.loop.loop_backend.Chat.dto.ChatMessagesResponseDto;
import com.loop.loop_backend.Chat.dto.ChatOtherUserRelationDto;
import com.loop.loop_backend.Chat.dto.ChatReadEventDto;
import com.loop.loop_backend.Chat.dto.ChatRoomResponseDto;
import com.loop.loop_backend.Chat.dto.ChatRoomSummaryDto;
import com.loop.loop_backend.Chat.dto.CreateChatRoomRequestDto;
import com.loop.loop_backend.Chat.dto.StartDirectChatRequestDto;
import com.loop.loop_backend.Chat.repository.ChatParticipantRepository;
import com.loop.loop_backend.Chat.repository.ChatRoomRepository;
import com.loop.loop_backend.Chat.repository.MessageRepository;
import com.loop.loop_backend.Block.repository.BlockRepository;
import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
import com.loop.loop_backend.CompanionPost.repository.CompanionPostRepository;
import com.loop.loop_backend.Report.repository.ReportRepository;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import com.loop.loop_backend.common.util.HtmlSanitizer;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatServiceImpl implements ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final MessageRepository messageRepository;
    private final BlockRepository blockRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final CompanionPostRepository companionPostRepository;
    private final EntityManager em;
    private final SimpMessagingTemplate messagingTemplate;
    private final TransactionTemplate transactionTemplate;

    @Override
    @Transactional
    public ChatRoomResponseDto createRoom(Long requesterId, CreateChatRoomRequestDto request) {
        CompanionPost post = companionPostRepository.findById(request.getPostId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANION_POST_NOT_FOUND));

        Long hostId = post.getUser().getId();
        if (hostId.equals(requesterId)) {
            throw new BusinessException(ErrorCode.SELF_CHAT_NOT_ALLOWED);
        }

        if (chatRoomRepository.existsByPost_Id(request.getPostId())) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_ALREADY_EXISTS);
        }

        User host = post.getUser();
        User applicant = em.getReference(User.class, requesterId);

        String name = request.getName() != null ? request.getName()
                : "동행 채팅방 #" + request.getPostId();

        ChatRoom room = ChatRoom.builder()
                .post(post)
                .name(name)
                .type(request.getType())
                .build();
        chatRoomRepository.save(room);

        chatParticipantRepository.save(ChatParticipant.builder()
                .chatRoom(room).user(host).role(ParticipantRole.HOST).status(ParticipantStatus.ACTIVE)
                .build());
        chatParticipantRepository.save(ChatParticipant.builder()
                .chatRoom(room).user(applicant).role(ParticipantRole.MEMBER).status(ParticipantStatus.ACTIVE)
                .build());

        return ChatRoomResponseDto.from(room);
    }

    @Override
    // 클래스 레벨 @Transactional(readOnly=true) 를 이 메서드에서 suspend.
    //  1) 안 하면 transactionTemplate.execute 안의 INSERT 가 readOnly connection 에서 거부됨.
    //  2) 대신 @Transactional 만 붙이면 tx commit 이 메서드 반환 시점 = synchronized 블록 이후라
    //     mutex 를 놓은 순간에도 다른 스레드에게 room 이 안 보여 중복 생성이 재발함.
    //  → NOT_SUPPORTED 로 바깥 tx 를 걸어두지 않고, 안쪽 transactionTemplate 가 REQUIRED 로
    //     자기 tx 를 만들어 execute() 반환 시(=synchronized 안) 커밋되도록 보장.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChatRoomResponseDto startDirectChat(Long myUserId, StartDirectChatRequestDto request) {
        Long targetId = request.getTargetUserId();
        if (myUserId.equals(targetId)) {
            throw new BusinessException(ErrorCode.SELF_CHAT_NOT_ALLOWED);
        }

        // ponytail: 동일 페어 동시 생성 방지용 JVM 내 mutex. sync가 tx를 감싸야 commit이
        // 다음 스레드 조회에 보인다. 다중 인스턴스면 (type, min(user), max(user)) DB 유니크로 승격.
        String pairKey = ("chat:direct:"
                + Math.min(myUserId, targetId) + ":" + Math.max(myUserId, targetId)).intern();
        Long roomId;
        synchronized (pairKey) {
            roomId = transactionTemplate.execute(status -> {
                // LINE 방식: DIRECT는 페어당 방 1개. hide된(내 participant=LEFT) 방이면 rejoin.
                List<ChatRoom> existing = chatRoomRepository.findDirectRoomsBetweenAnyStatus(myUserId, targetId);
                ChatRoom room;
                if (existing.isEmpty()) {
                    room = createDirectRoom(myUserId, targetId);
                } else {
                    room = existing.get(0);
                    chatParticipantRepository.findByChatRoom_IdAndUser_Id(room.getId(), myUserId)
                            .filter(p -> p.getStatus() == ParticipantStatus.LEFT)
                            .ifPresent(ChatParticipant::rejoin);
                }
                if (request.getCompanionPostId() != null) {
                    CompanionPost post = companionPostRepository.findById(request.getCompanionPostId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.COMPANION_POST_NOT_FOUND));
                    if (!post.getUser().getId().equals(targetId)) {
                        throw new BusinessException(ErrorCode.FORBIDDEN);
                    }
                    room.assignPost(post);
                }
                return room.getId();
            });
        }

        ChatRoomSummaryDto summary = chatRoomRepository.findSummaryById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        User otherUser = userRepository.findById(targetId).orElse(null);
        ChatOtherUserRelationDto relation = buildRelation(myUserId, targetId, otherUser);
        return ChatRoomResponseDto.fromSummary(summary, targetId, otherUser, relation);
    }

    private ChatRoom createDirectRoom(Long myUserId, Long targetId) {
        User me = em.getReference(User.class, myUserId);
        User target = em.getReference(User.class, targetId);
        ChatRoom created = ChatRoom.builder()
                .type(ChatRoomType.DIRECT)
                .name(null)
                .build();
        chatRoomRepository.save(created);
        chatParticipantRepository.save(ChatParticipant.builder()
                .chatRoom(created).user(me).role(ParticipantRole.HOST).status(ParticipantStatus.ACTIVE)
                .build());
        chatParticipantRepository.save(ChatParticipant.builder()
                .chatRoom(created).user(target).role(ParticipantRole.MEMBER).status(ParticipantStatus.ACTIVE)
                .build());
        return created;
    }

    @Override
    @Transactional
    public ChatRoomResponseDto joinRoom(Long roomId, Long userId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (room.getType() == ChatRoomType.DIRECT) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        chatParticipantRepository.findByChatRoom_IdAndUser_Id(roomId, userId)
                .ifPresentOrElse(
                        existing -> {
                            if (existing.getStatus() == ParticipantStatus.LEFT) {
                                existing.rejoin();
                            }
                        },
                        () -> {
                            User user = em.getReference(User.class, userId);
                            chatParticipantRepository.save(ChatParticipant.builder()
                                    .chatRoom(room).user(user)
                                    .role(ParticipantRole.MEMBER).status(ParticipantStatus.ACTIVE)
                                    .build());
                        }
                );

        return ChatRoomResponseDto.from(room);
    }

    @Override
    @Transactional
    public void leaveRoom(Long roomId, Long userId) {
        ChatParticipant participant = chatParticipantRepository
                .findByChatRoom_IdAndUser_Id(roomId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CHAT_PARTICIPANT));

        if (participant.getStatus() == ParticipantStatus.LEFT) {
            throw new BusinessException(ErrorCode.ALREADY_LEFT_CHAT);
        }
        participant.leave();

        User leaver = participant.getUser();
        String leaverNickname = leaver.getNickname() != null ? leaver.getNickname() : "상대방";
        saveAndBroadcastSystemMessage(
                em.getReference(ChatRoom.class, roomId),
                leaver,
                MessageType.SYSTEM_LEAVE,
                leaverNickname + "님이 채팅방을 나갔습니다");

        ChatLeaveEventDto event = ChatLeaveEventDto.builder()
                .type(ChatLeaveEventDto.Type.LEAVE)
                .roomId(roomId)
                .leaverId(userId)
                .leftAt(LocalDateTime.now())
                .build();
        messagingTemplate.convertAndSend("/sub/chat/room/" + roomId, event);
    }

    @Override
    public List<ChatRoomResponseDto> getMyRooms(Long userId) {
        return chatRoomRepository.findActiveRoomsByUserId(userId).stream()
                .map(room -> {
                    User otherUser = chatParticipantRepository
                            .findByChatRoom_IdAndStatus(room.getId(), ParticipantStatus.ACTIVE).stream()
                            .filter(p -> !p.getUser().getId().equals(userId))
                            .map(ChatParticipant::getUser)
                            .findFirst()
                            .orElseGet(() -> chatParticipantRepository
                                    .findByChatRoom_IdAndStatus(room.getId(), ParticipantStatus.LEFT).stream()
                                    .filter(p -> !p.getUser().getId().equals(userId))
                                    .map(ChatParticipant::getUser)
                                    .findFirst()
                                    .orElse(null));
                    Long otherUserId = otherUser != null ? otherUser.getId() : null;
                    Message lastMessage = messageRepository
                            .findTopByChatRoom_IdOrderByCreatedAtDesc(room.getId())
                            .orElse(null);
                    long unreadCount = messageRepository
                            .countByChatRoom_IdAndSender_IdNotAndIsReadFalse(room.getId(), userId);
                    return ChatRoomResponseDto.forList(room, otherUserId, otherUser, lastMessage, unreadCount);
                })
                .collect(Collectors.toList());
    }

    @Override
    public ChatMessagesResponseDto getMessages(Long roomId, Long userId, Pageable pageable) {
        assertActiveParticipant(roomId, userId);

        Slice<ChatMessageDto> messages = messageRepository.findByChatRoom_IdOrderByCreatedAtDesc(roomId, pageable)
                .map(this::toDto);

        ChatRoom room = chatRoomRepository.findById(roomId).orElse(null);
        ChatOtherUserRelationDto relation = buildDirectRelationForRoom(room, userId);

        ChatMessagesResponseDto.ChatMessagesResponseDtoBuilder builder = ChatMessagesResponseDto.builder()
                .otherUserRelation(relation)
                .messages(messages);
        if (room != null && room.getPost() != null) {
            builder.otherCompanionId(room.getPost().getId());
            if (room.getPost().getConcert() != null) {
                builder.concertId(room.getPost().getConcert().getId());
            }
        }
        return builder.build();
    }

    @Override
    @Transactional
    public void markAsRead(Long roomId, Long userId) {
        assertActiveParticipant(roomId, userId);
        messageRepository.markAllAsRead(roomId, userId);

        ChatReadEventDto event = ChatReadEventDto.builder()
                .type(ChatReadEventDto.Type.READ)
                .roomId(roomId)
                .readerId(userId)
                .readAt(LocalDateTime.now())
                .build();
        messagingTemplate.convertAndSend("/sub/chat/room/" + roomId, event);
    }

    @Override
    @Transactional
    public ChatMessageDto saveMessage(Long roomId, Long senderId, String rawContent) {
        assertActiveParticipant(roomId, senderId);
        assertNotBlockedInRoom(roomId, senderId);
        assertOtherParticipantNotWithdrawn(roomId, senderId);
        assertOtherParticipantActive(roomId, senderId);

        String sanitized = HtmlSanitizer.sanitize(rawContent);
        if (sanitized == null || sanitized.isBlank()) {
            throw new BusinessException(ErrorCode.MESSAGE_CONTENT_INVALID);
        }

        ChatRoom chatRoom = em.getReference(ChatRoom.class, roomId);
        User sender = em.getReference(User.class, senderId);
        Message saved = messageRepository.save(Message.builder()
                .chatRoom(chatRoom)
                .sender(sender)
                .type(MessageType.USER)
                .content(sanitized)
                .build());

        return ChatMessageDto.builder()
                .type(ChatMessageDto.MessageType.TALK)
                .roomId(roomId)
                .senderId(senderId)
                .content(sanitized)
                .createdAt(saved.getCreatedAt() != null ? saved.getCreatedAt() : LocalDateTime.now())
                .isRead(false)
                .build();
    }

    @Override
    @Transactional
    public void hideDirectRoomForUser(Long actorUserId, Long otherUserId) {
        // 중복 방(과거 race로 생긴 dupes)까지 전부 hide
        chatRoomRepository.findDirectRoomsBetweenAnyStatus(actorUserId, otherUserId)
                .forEach(room -> chatParticipantRepository
                        .findByChatRoom_IdAndUser_Id(room.getId(), actorUserId)
                        .filter(p -> p.getStatus() == ParticipantStatus.ACTIVE)
                        .ifPresent(ChatParticipant::leave));
    }

    @Override
    @Transactional
    public void handleUserWithdrawn(Long withdrawnUserId) {
        User withdrawer = userRepository.findById(withdrawnUserId).orElse(null);
        if (withdrawer == null) return;
        String nickname = withdrawer.getNickname() != null ? withdrawer.getNickname() : "상대방";
        String content = nickname + "님이 루프를 탈퇴했어요";

        // 탈퇴자가 참여 중이던(ACTIVE) DIRECT 방들에 대해 시스템 메시지 이력 남기고 실시간 브로드캐스트
        chatParticipantRepository.findByUser_IdAndStatus(withdrawnUserId, ParticipantStatus.ACTIVE).stream()
                .map(ChatParticipant::getChatRoom)
                .filter(room -> room.getType() == ChatRoomType.DIRECT)
                .forEach(room -> saveAndBroadcastSystemMessage(room, withdrawer, MessageType.SYSTEM_WITHDRAWN, content));
    }

    private void saveAndBroadcastSystemMessage(ChatRoom room, User actor, MessageType type, String content) {
        Message saved = messageRepository.save(Message.builder()
                .chatRoom(room)
                .sender(actor)
                .type(type)
                .content(content)
                .build());
        messagingTemplate.convertAndSend("/sub/chat/room/" + room.getId(), toDto(saved));
    }

    private ChatMessageDto toDto(Message m) {
        return ChatMessageDto.builder()
                .type(mapType(m.getType()))
                .roomId(m.getChatRoom().getId())
                .senderId(m.getSender() != null ? m.getSender().getId() : null)
                .content(m.getContent())
                .createdAt(m.getCreatedAt())
                .isRead(m.isRead())
                .build();
    }

    private ChatMessageDto.MessageType mapType(MessageType type) {
        if (type == null) return ChatMessageDto.MessageType.TALK;
        return switch (type) {
            case USER -> ChatMessageDto.MessageType.TALK;
            case SYSTEM_LEAVE -> ChatMessageDto.MessageType.SYSTEM_LEAVE;
            case SYSTEM_WITHDRAWN -> ChatMessageDto.MessageType.SYSTEM_WITHDRAWN;
        };
    }

    private void assertOtherParticipantActive(Long roomId, Long senderId) {
        List<ChatParticipant> others = chatParticipantRepository.findByChatRoom_IdAndStatus(roomId, ParticipantStatus.ACTIVE).stream()
                .filter(p -> !p.getUser().getId().equals(senderId))
                .collect(Collectors.toList());
        if (others.isEmpty()) {
            throw new BusinessException(ErrorCode.OTHER_USER_LEFT);
        }
    }

    private void assertActiveParticipant(Long roomId, Long userId) {
        if (!chatParticipantRepository.existsByChatRoom_IdAndUser_IdAndStatus(
                roomId, userId, ParticipantStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.NOT_CHAT_PARTICIPANT);
        }
    }

    private void assertNotBlockedInRoom(Long roomId, Long senderId) {
        List<Long> otherIds = chatParticipantRepository
                .findByChatRoom_IdAndStatus(roomId, ParticipantStatus.ACTIVE).stream()
                .map(p -> p.getUser().getId())
                .filter(id -> !id.equals(senderId))
                .collect(Collectors.toList());

        if (otherIds.isEmpty()) return;

        if (blockRepository.existsBlockBetween(senderId, otherIds)) {
            throw new BusinessException(ErrorCode.BLOCKED_USER);
        }
    }

    // ponytail: N+1 for otherIds > 1. 그룹 채팅이 프로덕션에서 실제 사용되면 IN 쿼리 하나로 대체
    private void assertOtherParticipantNotWithdrawn(Long roomId, Long senderId) {
        List<Long> otherIds = chatParticipantRepository
                .findByChatRoom_IdAndStatus(roomId, ParticipantStatus.ACTIVE).stream()
                .map(p -> p.getUser().getId())
                .filter(id -> !id.equals(senderId))
                .collect(Collectors.toList());

        for (Long otherId : otherIds) {
            User other = userRepository.findById(otherId).orElse(null);
            if (other != null && other.getStatus() == Status.WITHDRAWN) {
                throw new BusinessException(ErrorCode.OTHER_USER_WITHDRAWN);
            }
        }
    }

    private ChatOtherUserRelationDto buildDirectRelationForRoom(ChatRoom room, Long myUserId) {
        if (room == null || room.getType() != ChatRoomType.DIRECT) return null;
        Long roomId = room.getId();

        Long otherId = chatParticipantRepository.findByChatRoom_IdAndStatus(roomId, ParticipantStatus.ACTIVE).stream()
                .map(p -> p.getUser().getId())
                .filter(id -> !id.equals(myUserId))
                .findFirst()
                .orElseGet(() -> chatParticipantRepository.findByChatRoom_IdAndStatus(roomId, ParticipantStatus.LEFT).stream()
                        .map(p -> p.getUser().getId())
                        .filter(id -> !id.equals(myUserId))
                        .findFirst()
                        .orElse(null));

        if (otherId == null) return null;
        User otherUser = userRepository.findById(otherId).orElse(null);
        return buildRelation(myUserId, otherId, otherUser);
    }

    private ChatOtherUserRelationDto buildRelation(Long myUserId, Long otherId, User otherUser) {
        if (otherUser == null) {
            return ChatOtherUserRelationDto.builder()
                    .otherUserWithdrawn(true)
                    .build();
        }
        return ChatOtherUserRelationDto.builder()
                .otherUserWithdrawn(otherUser.getStatus() == Status.WITHDRAWN)
                .blockedByMe(blockRepository.existsByBlocker_IdAndBlocked_Id(myUserId, otherId))
                .blockedMe(blockRepository.existsByBlocker_IdAndBlocked_Id(otherId, myUserId))
                .reportedByMe(reportRepository.existsByReporter_IdAndTargetUser_Id(myUserId, otherId))
                .build();
    }
}
