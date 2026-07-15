package com.loop.loop_backend.Chat.service;

import com.loop.loop_backend.Chat.domain.ChatParticipant;
import com.loop.loop_backend.Chat.domain.ChatRoom;
import com.loop.loop_backend.Chat.domain.ChatRoomType;
import com.loop.loop_backend.Chat.domain.Message;
import com.loop.loop_backend.Chat.domain.ParticipantRole;
import com.loop.loop_backend.Chat.domain.ParticipantStatus;
import com.loop.loop_backend.Chat.dto.ChatMessageDto;
import com.loop.loop_backend.Chat.dto.ChatRoomResponseDto;
import com.loop.loop_backend.Chat.dto.CreateChatRoomRequestDto;
import com.loop.loop_backend.Chat.dto.StartDirectChatRequestDto;
import com.loop.loop_backend.Chat.repository.ChatParticipantRepository;
import com.loop.loop_backend.Chat.repository.ChatRoomRepository;
import com.loop.loop_backend.Chat.repository.MessageRepository;
import com.loop.loop_backend.Block.repository.BlockRepository;
import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
import com.loop.loop_backend.CompanionPost.repository.CompanionPostRepository;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import com.loop.loop_backend.common.util.HtmlSanitizer;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final CompanionPostRepository companionPostRepository;
    private final EntityManager em;

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
    @Transactional
    public ChatRoomResponseDto startDirectChat(Long myUserId, StartDirectChatRequestDto request) {
        Long targetId = request.getTargetUserId();
        if (myUserId.equals(targetId)) {
            throw new BusinessException(ErrorCode.SELF_CHAT_NOT_ALLOWED);
        }

        return chatRoomRepository.findDirectRoomBetween(myUserId, targetId)
                .map(ChatRoomResponseDto::from)
                .orElseGet(() -> {
                    User me = em.getReference(User.class, myUserId);
                    User target = em.getReference(User.class, targetId);

                    ChatRoom room = ChatRoom.builder()
                            .type(ChatRoomType.DIRECT)
                            .name(null)
                            .build();
                    chatRoomRepository.save(room);

                    chatParticipantRepository.save(ChatParticipant.builder()
                            .chatRoom(room).user(me).role(ParticipantRole.HOST).status(ParticipantStatus.ACTIVE)
                            .build());
                    chatParticipantRepository.save(ChatParticipant.builder()
                            .chatRoom(room).user(target).role(ParticipantRole.MEMBER).status(ParticipantStatus.ACTIVE)
                            .build());

                    return ChatRoomResponseDto.from(room);
                });
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
                            .orElse(null);
                    Message lastMessage = messageRepository
                            .findTopByChatRoom_IdOrderByCreatedAtDesc(room.getId())
                            .orElse(null);
                    long unreadCount = messageRepository
                            .countByChatRoom_IdAndSender_IdNotAndIsReadFalse(room.getId(), userId);
                    return ChatRoomResponseDto.forList(room, otherUser, lastMessage, unreadCount);
                })
                .collect(Collectors.toList());
    }

    @Override
    public Slice<ChatMessageDto> getMessages(Long roomId, Long userId, Pageable pageable) {
        assertActiveParticipant(roomId, userId);
        return messageRepository.findByChatRoom_IdOrderByCreatedAtDesc(roomId, pageable)
                .map(m -> ChatMessageDto.builder()
                        .type(ChatMessageDto.MessageType.TALK)
                        .roomId(m.getChatRoom().getId())
                        .senderId(m.getSender().getId())
                        .content(m.getContent())
                        .createdAt(m.getCreatedAt())
                        .build());
    }

    @Override
    @Transactional
    public void markAsRead(Long roomId, Long userId) {
        assertActiveParticipant(roomId, userId);
        messageRepository.markAllAsRead(roomId, userId);
    }

    @Override
    @Transactional
    public ChatMessageDto saveMessage(Long roomId, Long senderId, String rawContent) {
        assertActiveParticipant(roomId, senderId);
        assertNotBlockedInRoom(roomId, senderId);

        String sanitized = HtmlSanitizer.sanitize(rawContent);
        if (sanitized == null || sanitized.isBlank()) {
            throw new BusinessException(ErrorCode.MESSAGE_CONTENT_INVALID);
        }

        ChatRoom chatRoom = em.getReference(ChatRoom.class, roomId);
        User sender = em.getReference(User.class, senderId);
        Message saved = messageRepository.save(Message.builder()
                .chatRoom(chatRoom)
                .sender(sender)
                .content(sanitized)
                .build());

        return ChatMessageDto.builder()
                .type(ChatMessageDto.MessageType.TALK)
                .roomId(roomId)
                .senderId(senderId)
                .content(sanitized)
                .createdAt(saved.getCreatedAt() != null ? saved.getCreatedAt() : LocalDateTime.now())
                .build();
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
}
