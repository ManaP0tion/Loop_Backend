package com.loop.loop_backend.Chat.service;

import com.loop.loop_backend.Chat.domain.ChatParticipant;
import com.loop.loop_backend.Chat.domain.ChatRoom;
import com.loop.loop_backend.Chat.domain.Message;
import com.loop.loop_backend.Chat.domain.ParticipantRole;
import com.loop.loop_backend.Chat.domain.ParticipantStatus;
import com.loop.loop_backend.Chat.domain.ChatRoomType;
import com.loop.loop_backend.Chat.dto.ChatMessageDto;
import com.loop.loop_backend.Chat.dto.ChatRoomResponseDto;
import com.loop.loop_backend.Chat.dto.CreateChatRoomRequestDto;
import com.loop.loop_backend.Chat.dto.StartDirectChatRequestDto;
import com.loop.loop_backend.Chat.repository.ChatParticipantRepository;
import com.loop.loop_backend.Chat.repository.ChatRoomRepository;
import com.loop.loop_backend.Chat.repository.MessageRepository;
import com.loop.loop_backend.Block.repository.BlockRepository;
import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final UserRepository userRepository;
    private final EntityManager em;

    @Override
    @Transactional
    public ChatRoomResponseDto createRoom(CreateChatRoomRequestDto request) {
        if (chatRoomRepository.existsByPost_Id(request.getPostId())) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_ALREADY_EXISTS);
        }

        CompanionPost post = em.getReference(CompanionPost.class, request.getPostId());
        User host = em.getReference(User.class, request.getHostUserId());
        User applicant = em.getReference(User.class, request.getApplicantUserId());

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
    public ChatRoomResponseDto startDirectChat(StartDirectChatRequestDto request) {
        Long myId = request.getMyUserId();
        Long targetId = request.getTargetUserId();

        return chatRoomRepository.findDirectRoomBetween(myId, targetId)
                .map(ChatRoomResponseDto::from)
                .orElseGet(() -> {
                    User me = em.getReference(User.class, myId);
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
    public List<ChatRoomResponseDto> getRoomsByUser(Long userId) {
        List<ChatRoom> rooms = (userId == null)
                ? chatRoomRepository.findAll()
                : chatRoomRepository.findActiveRoomsByUserId(userId);
        return rooms.stream().map(ChatRoomResponseDto::from).collect(Collectors.toList());
    }

    @Override
    public List<?> getMessages(Long roomId) {
        return messageRepository.findByChatRoom_IdOrderByCreatedAtAsc(roomId);
    }

    @Override
    @Transactional
    public void saveMessage(ChatMessageDto dto) {
        ChatRoom chatRoom = em.getReference(ChatRoom.class, dto.getRoomId());
        User sender = em.getReference(User.class, dto.getSenderId());
        messageRepository.save(Message.builder()
                .chatRoom(chatRoom)
                .sender(sender)
                .content(dto.getContent())
                .build());
    }

    @Override
    public boolean canChat(Long senderId, Long roomId) {
        if (!chatParticipantRepository.existsByChatRoom_IdAndUser_IdAndStatus(
                roomId, senderId, ParticipantStatus.ACTIVE)) {
            return false;
        }

        List<Long> otherIds = chatParticipantRepository
                .findByChatRoom_IdAndStatus(roomId, ParticipantStatus.ACTIVE).stream()
                .map(p -> p.getUser().getId())
                .filter(id -> !id.equals(senderId))
                .collect(Collectors.toList());

        if (otherIds.isEmpty()) {
            return true;
        }

        return !blockRepository.existsBlockBetween(senderId, otherIds);
    }
}
