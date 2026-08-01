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
import com.loop.loop_backend.Mail.service.MailService;
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
    private final MailService mailService;
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
                .name(name)
                .type(request.getType())
                .build();
        room.assignPost(post); // post + concertId(스냅샷) 동시 세팅, 빌더에서 직접 넣지 않고 이 메서드로 통일
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
        boolean[] isNewRoom = {false}; //새 채팅 메일링 용
        String[] concertTitleHolder = {null}; //새 채팅 메일링 용 — post.concert는 LAZY라 tx 안에서 미리 읽어둠
        boolean[] rejoined = {false};
        synchronized (pairKey) {
            roomId = transactionTemplate.execute(status -> {
                // LINE 방식: DIRECT는 페어당 방 1개. hide된(내 participant=LEFT) 방이면 rejoin.
                List<ChatRoom> existing = chatRoomRepository.findDirectRoomsBetweenAnyStatus(myUserId, targetId);
                ChatRoom room;
                if (existing.isEmpty()) {
                    room = createDirectRoom(myUserId, targetId);
                    isNewRoom[0] = true;  //새 채팅 메일링 용
                } else {
                    room = existing.get(0);
                    ChatRoom rejoinRoom = room;
                    chatParticipantRepository.findByChatRoom_IdAndUser_Id(room.getId(), myUserId)
                            .filter(p -> p.getStatus() == ParticipantStatus.LEFT)
                            .ifPresent(p -> {
                                // messageVisibleFrom이 이미 있다는 건 이 LEFT가 일반 나가기가 아니라
                                // 회원 탈퇴(handleUserWithdrawn)로 인한 것이었다는 뜻 — 그 경우에만
                                // 아래에서 상대 쪽도 새로 시작하는 것처럼 리셋해준다.
                                boolean rejoinAfterWithdrawal = p.getMessageVisibleFrom() != null;
                                p.rejoin();
                                rejoined[0] = true;
                                // REST 재입장 히스토리에 노출할 시스템 메시지 영속화(소켓 REJOIN 이벤트와 별개).
                                User me = p.getUser();
                                String nickname = me.getNickname() != null ? me.getNickname() : "상대방";
                                Message systemMsg = messageRepository.save(Message.builder()
                                        .chatRoom(rejoinRoom).sender(me).type(MessageType.SYSTEM_REJOIN)
                                        .content(nickname + "님이 다시 채팅방에 들어왔습니다").build());

                                // 탈퇴 후 재입장이면 상대(targetId) 쪽에도 커트라인을 찍어서, 이전 대화
                                // 전체(이 재입장 메시지 포함)가 상대에게도 조용히 안 보이게 한다.
                                // 메시지 row는 지우지 않으므로 신고 대응 등에서는 여전히 조회 가능.
                                // 일반적인 나가기 후 재입장은 상대 쪽 이력을 그대로 둔다(여기 안 들어옴).
                                if (rejoinAfterWithdrawal) {
                                    chatParticipantRepository.findByChatRoom_IdAndUser_Id(room.getId(), targetId)
                                            .ifPresent(other -> other.hideMessagesBefore(systemMsg.getCreatedAt()));
                                }
                            });
                }
                if (request.getCompanionPostId() != null) {
                    CompanionPost post = companionPostRepository.findById(request.getCompanionPostId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.COMPANION_POST_NOT_FOUND));
                    if (!post.getUser().getId().equals(targetId)) {
                        throw new BusinessException(ErrorCode.FORBIDDEN);
                    }
                    room.assignPost(post);
                    concertTitleHolder[0] = post.getConcert().getTitle();
                }
                return room.getId();
            });
        }

        // 커밋 후 브로드캐스트: 나갔던 내가 돌아왔음을 상대에게 알려 입력창 unblock 하도록.
        if (rejoined[0]) {
            messagingTemplate.convertAndSend("/sub/chat/room/" + roomId, ChatLeaveEventDto.builder()
                    .type(ChatLeaveEventDto.Type.REJOIN)
                    .roomId(roomId)
                    .leaverId(myUserId)
                    .build());
        }

        ChatRoomSummaryDto summary = chatRoomRepository.findSummaryById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        User otherUser = userRepository.findById(targetId).orElse(null);
        ChatOtherUserRelationDto relation = buildRelation(myUserId, targetId, otherUser);

        // 진짜 신규 생성(재입장 아님)일 때만, 채팅을 받은 쪽(상대방)에게 알림 메일 발송.
        // 상대방이 채팅 알림 메일을 꺼뒀으면 보내지 않는다.
        if (isNewRoom[0] && otherUser != null && otherUser.isChatNotificationEmail()) {
            String myNickname = userRepository.findById(myUserId).map(User::getNickname).orElse("회원");
            mailService.sendNewChatNotification(otherUser.getEmail(), otherUser.getNickname(), myNickname, concertTitleHolder[0]);
        }

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

                    // 나(userId) 참여 row의 messageVisibleFrom 커트라인을 읽어서, 목록 미리보기
                    // (마지막 메시지/안읽음 수)에도 getMessages()와 동일한 기준을 적용한다.
                    // 이게 없으면 상세 조회는 이력을 숨기는데 목록 미리보기에서 예전 마지막 메시지가
                    // 그대로 노출되는 불일치가 생긴다.
                    LocalDateTime cutoff = chatParticipantRepository
                            .findByChatRoom_IdAndUser_Id(room.getId(), userId)
                            .map(ChatParticipant::getMessageVisibleFrom)
                            .orElse(null);

                    Message lastMessage = (cutoff == null
                            ? messageRepository.findTopByChatRoom_IdOrderByCreatedAtDesc(room.getId())
                            : messageRepository.findTopByChatRoom_IdAndCreatedAtAfterOrderByCreatedAtDesc(room.getId(), cutoff))
                            .orElse(null);
                    long unreadCount = cutoff == null
                            ? messageRepository.countByChatRoom_IdAndSender_IdNotAndIsReadFalse(room.getId(), userId)
                            : messageRepository.countByChatRoom_IdAndSender_IdNotAndIsReadFalseAndCreatedAtAfter(room.getId(), userId, cutoff);
                    return ChatRoomResponseDto.forList(room, otherUserId, otherUser, lastMessage, unreadCount, userId);
                })
                .collect(Collectors.toList());
    }

    @Override
    public ChatMessagesResponseDto getMessages(Long roomId, Long userId, Pageable pageable) {
        // boolean 존재 체크(assertActiveParticipant) 대신 참여자 엔티티를 직접 가져온다.
        // messageVisibleFrom 커트라인을 읽어야 하기 때문. 검증 조건(ACTIVE 아니면 거부)은 동일하다.
        ChatParticipant me = chatParticipantRepository.findByChatRoom_IdAndUser_Id(roomId, userId)
                .filter(p -> p.getStatus() == ParticipantStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CHAT_PARTICIPANT));
        LocalDateTime cutoff = me.getMessageVisibleFrom();

        // cutoff가 없으면(일반 사용자, 또는 탈퇴 이력이 없는 참여자) 기존과 동일하게 전체 이력 조회.
        // cutoff가 있으면(탈퇴 후 재가입한 본인) 그 시각 이후 메시지만 조회해 예전 이력을 감춘다.
        Slice<ChatMessageDto> messages = (cutoff == null
                ? messageRepository.findByChatRoom_IdOrderByCreatedAtDesc(roomId, pageable)
                : messageRepository.findByChatRoom_IdAndCreatedAtAfterOrderByCreatedAtDesc(roomId, cutoff, pageable))
                .map(this::toDto);

        ChatRoom room = chatRoomRepository.findById(roomId).orElse(null);
        ChatOtherUserRelationDto relation = buildDirectRelationForRoom(room, userId);

        ChatMessagesResponseDto.ChatMessagesResponseDtoBuilder builder = ChatMessagesResponseDto.builder()
                .otherUserRelation(relation)
                .messages(messages);
        if (room != null) {
            // concertId는 room.post.concert가 아니라 방에 스냅샷으로 저장된 값(post 삭제돼도 안 끊김)
            if (room.getConcertId() != null) {
                builder.concertId(room.getConcertId());
            }
            Long otherCompanionId = resolveOtherCompanionId(room, userId);
            if (otherCompanionId != null) {
                builder.otherCompanionId(otherCompanionId);
            }
        }
        return builder.build();
    }

    // "채팅방에서 상대방 동행 프로필 열기" 링크로 쓸 상대의 CompanionPost id.
    // room.post는 페어당 최근 startDirectChat에서 assign된 "한쪽 글"만 담고 있어서
    // 보는 사람(viewerId)이 그 글 주인이냐에 따라 갈린다.
    //  1) room.post 주인 != 나  → 그 글이 곧 "상대 글"이므로 그대로 반환.
    //  2) room.post 주인 == 나(=내가 host) → room.post는 "내 글"이라 상대 프로필이 아니다.
    //     예전엔 이 경우를 그냥 room.post.id로 반환해서 host가 프로필을 누르면 "내 프로필"이
    //     열리는 버그가 있었고, 이후 null 처리로 막았지만 그러면 host는 상대 프로필을 아예 못 봤다.
    //     이제는 상대 참여자가 쓴 "같은 콘서트·같은 관람일(watchDay)" 글을 찾아 링크한다.
    //     채팅 시작 게이트(existsMyCompanion)가 "상대 글과 동일 concert+watchDay에 내 글이 있어야
    //     채팅 가능"을 이미 강제하고, companion_posts는 (user, concert, watchDay) 유니크라
    //     이 조회 결과는 항상 0 또는 1개다. 상대가 글을 지웠으면 null → 링크만 빠진다.
    // ponytail: "같은 watchDay" 커플링은 위 게이트 규칙을 그대로 따른 것. 규칙이 바뀌면 여기도 같이 손봐야 함.
    private Long resolveOtherCompanionId(ChatRoom room, Long viewerId) {
        if (room.getPost() == null) return null;
        if (!room.getPost().getUser().getId().equals(viewerId)) {
            return room.getPost().getId();
        }
        if (room.getConcertId() == null) return null;
        Long otherId = resolveOtherUserId(room.getId(), viewerId);
        if (otherId == null) return null;
        return companionPostRepository
                .findByUser_IdAndConcert_IdAndWatchDay(otherId, room.getConcertId(), room.getPost().getWatchDay())
                .map(CompanionPost::getId)
                .orElse(null);
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

        // 탈퇴자가 참여 중이던(ACTIVE) DIRECT 방마다:
        //  1) 시스템 메시지 남기고 실시간 브로드캐스트
        //  2) 탈퇴자 자신의 참여 상태를 LEFT로 전환 → 탈퇴자 본인의 getMyRooms()에서 이 방이 즉시 빠짐.
        //     상대방 참여 row는 건드리지 않으므로 상대방 목록/이력엔 영향 없음.
        //  3) messageVisibleFrom을 방금 남긴 시스템 메시지 시각으로 세팅 → 이 메시지 포함, 그 이전
        //     대화 전체가 "이 참여자에게는" 숨겨짐. reactivate()는 User 필드만 초기화하고 이 값을
        //     건드리지 않으므로, 나중에 재가입해서 같은 상대와 대화를 재개해도(rejoin()) 탈퇴 이전
        //     이력은 계속 안 보인다.
        chatParticipantRepository.findByUser_IdAndStatus(withdrawnUserId, ParticipantStatus.ACTIVE).stream()
                .filter(p -> p.getChatRoom().getType() == ChatRoomType.DIRECT)
                .forEach(p -> {
                    Message systemMsg = saveAndBroadcastSystemMessage(
                            p.getChatRoom(), withdrawer, MessageType.SYSTEM_WITHDRAWN, content);
                    p.leave();
                    p.hideMessagesBefore(systemMsg.getCreatedAt());
                });
    }

    // 저장된 Message를 반환하도록 해서, 호출부(handleUserWithdrawn)가 이 메시지의 createdAt을
    // messageVisibleFrom 커트라인으로 그대로 재사용할 수 있게 한다. 기존 호출부(leaveRoom)는
    // 반환값을 쓰지 않아도 되므로 영향 없음.
    private Message saveAndBroadcastSystemMessage(ChatRoom room, User actor, MessageType type, String content) {
        Message saved = messageRepository.save(Message.builder()
                .chatRoom(room)
                .sender(actor)
                .type(type)
                .content(content)
                .build());
        messagingTemplate.convertAndSend("/sub/chat/room/" + room.getId(), toDto(saved));
        return saved;
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
            case SYSTEM_REJOIN -> ChatMessageDto.MessageType.SYSTEM_REJOIN;
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
    //
    // 참여자 status(ACTIVE/LEFT)가 아니라 방의 전체 참여자를 기준으로 본다. 탈퇴 처리
    // (handleUserWithdrawn)가 탈퇴자 본인의 참여 row를 LEFT로 바꿔버리기 때문에, 여기서 ACTIVE만
    // 필터링하면 탈퇴자가 "다른 참여자 목록"에서 아예 빠져버려 탈퇴 여부를 못 잡는다. 그 결과 바로
    // 아래 assertOtherParticipantActive()에서 "상대가 없음"으로 걸려 OTHER_USER_LEFT(상대가 나갔다는
    // 문구)가 뜨게 되는데, 실제 사유는 나간 게 아니라 탈퇴이므로 사용자에게 부정확한 메시지가 노출된다.
    // 상태와 무관하게 상대 유저를 찾아야 OTHER_USER_WITHDRAWN(상대가 탈퇴했다는 정확한 문구)이 우선
    // 적용된다.
    private void assertOtherParticipantNotWithdrawn(Long roomId, Long senderId) {
        List<Long> otherIds = chatParticipantRepository
                .findByChatRoom_Id(roomId).stream()
                .map(p -> p.getUser().getId())
                .filter(id -> !id.equals(senderId))
                .distinct()
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

        Long otherId = resolveOtherUserId(room.getId(), myUserId);
        if (otherId == null) return null;
        User otherUser = userRepository.findById(otherId).orElse(null);
        return buildRelation(myUserId, otherId, otherUser);
    }

    // 방의 "나 아닌 참여자" id. ACTIVE 우선, 없으면 LEFT(나갔거나 탈퇴로 숨긴 상대)에서 찾는다.
    private Long resolveOtherUserId(Long roomId, Long myUserId) {
        return chatParticipantRepository.findByChatRoom_IdAndStatus(roomId, ParticipantStatus.ACTIVE).stream()
                .map(p -> p.getUser().getId())
                .filter(id -> !id.equals(myUserId))
                .findFirst()
                .orElseGet(() -> chatParticipantRepository.findByChatRoom_IdAndStatus(roomId, ParticipantStatus.LEFT).stream()
                        .map(p -> p.getUser().getId())
                        .filter(id -> !id.equals(myUserId))
                        .findFirst()
                        .orElse(null));
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
