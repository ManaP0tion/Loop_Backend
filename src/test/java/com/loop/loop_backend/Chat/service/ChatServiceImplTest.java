package com.loop.loop_backend.Chat.service;

import com.loop.loop_backend.Block.repository.BlockRepository;
import com.loop.loop_backend.Chat.domain.ChatParticipant;
import com.loop.loop_backend.Chat.domain.ChatRoom;
import com.loop.loop_backend.Chat.domain.ChatRoomType;
import com.loop.loop_backend.Chat.domain.Message;
import com.loop.loop_backend.Chat.domain.MessageType;
import com.loop.loop_backend.Chat.domain.ParticipantRole;
import com.loop.loop_backend.Chat.domain.ParticipantStatus;
import com.loop.loop_backend.Chat.dto.ChatMessageDto;
import com.loop.loop_backend.Chat.dto.ChatMessagesResponseDto;
import com.loop.loop_backend.Chat.dto.ChatRoomResponseDto;
import com.loop.loop_backend.Chat.repository.ChatParticipantRepository;
import com.loop.loop_backend.Chat.repository.ChatRoomRepository;
import com.loop.loop_backend.Chat.repository.MessageRepository;
import com.loop.loop_backend.CompanionPost.repository.CompanionPostRepository;
import com.loop.loop_backend.Mail.service.MailService;
import com.loop.loop_backend.Report.repository.ReportRepository;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatParticipant/Message 레포지토리를 Mockito mock 위에 인메모리 리스트로 얹어 실제 DB처럼
 * 동작하게 만들고(BlockServiceImplTest와 동일한 방식), 그 위에서 ChatServiceImpl의 실제 동작을
 * "회원 탈퇴가 채팅에 미치는 영향"이라는 요구사항 관점에서 블랙박스로 검증한다.
 *
 * 검증하는 요구사항 3가지:
 *  1) 탈퇴한 사람 자신 - 채팅방이 목록에서 사라져야 하고, 재가입해도 다시 보이면 안 된다.
 *  2) 상대방 - 상대가 탈퇴해도 채팅방/이력을 계속 볼 수 있어야 하지만, 메시지 전송은 막혀야 한다.
 *  3) 재가입 후 같은 상대와 대화를 재개해도, 재가입한 본인에게는 탈퇴 이전 이력이 다시 보이면
 *     안 되고(messageVisibleFrom 커트라인), 상대방에게는 그대로 보여야 한다.
 *
 * 파생 쿼리 메서드 이름 자체가 올바른 JPQL로 변환되는지는 이 테스트로는 검증되지 않는다
 * (Mockito는 실제 Spring Data 파싱을 거치지 않음) - 그 부분은 MessageRepositoryTest(@DataJpaTest)가 담당.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceImplTest {

    @Mock ChatRoomRepository chatRoomRepository;
    @Mock ChatParticipantRepository chatParticipantRepository;
    @Mock MessageRepository messageRepository;
    @Mock BlockRepository blockRepository;
    @Mock ReportRepository reportRepository;
    @Mock UserRepository userRepository;
    @Mock CompanionPostRepository companionPostRepository;
    @Mock MailService mailService;
    @Mock SimpMessagingTemplate messagingTemplate;
    // EntityManager, TransactionTemplate은 일부러 목(mock)으로도 안 만듦: 이번에 테스트하는
    // handleUserWithdrawn/getMessages/getMyRooms/saveMessage(차단 케이스)는 이 둘을 전혀 쓰지 않는다
    // (em/transactionTemplate은 createRoom/startDirectChat/joinRoom 같은, 이번 변경과 무관한
    // 메서드에서만 쓰임). @InjectMocks가 생성자에 null을 넣어줘도 테스트 대상 메서드는 문제없다.
    @InjectMocks ChatServiceImpl chatService;

    // ── 인메모리 "DB" ────────────────────────────────────────────────────────
    private final List<ChatParticipant> participantTable = new ArrayList<>();
    private final List<Message> messageTable = new ArrayList<>();
    private final Map<Long, User> userTable = new HashMap<>();
    private final AtomicLong messageClock = new AtomicLong();
    private LocalDateTime clockBase;

    @BeforeEach
    void setUp() {
        participantTable.clear();
        messageTable.clear();
        userTable.clear();
        messageClock.set(0);
        clockBase = LocalDateTime.of(2026, 1, 1, 0, 0, 0);

        when(userRepository.findById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(userTable.get(inv.getArgument(0, Long.class))));

        when(chatParticipantRepository.findByUser_IdAndStatus(anyLong(), any(ParticipantStatus.class)))
                .thenAnswer(inv -> participantTable.stream()
                        .filter(p -> p.getUser().getId().equals(inv.getArgument(0, Long.class)))
                        .filter(p -> p.getStatus() == inv.getArgument(1, ParticipantStatus.class))
                        .collect(Collectors.toList()));

        when(chatParticipantRepository.findByChatRoom_IdAndStatus(anyLong(), any(ParticipantStatus.class)))
                .thenAnswer(inv -> participantTable.stream()
                        .filter(p -> p.getChatRoom().getId().equals(inv.getArgument(0, Long.class)))
                        .filter(p -> p.getStatus() == inv.getArgument(1, ParticipantStatus.class))
                        .collect(Collectors.toList()));

        when(chatParticipantRepository.findByChatRoom_Id(anyLong()))
                .thenAnswer(inv -> participantTable.stream()
                        .filter(p -> p.getChatRoom().getId().equals(inv.getArgument(0, Long.class)))
                        .collect(Collectors.toList()));

        when(chatParticipantRepository.findByChatRoom_IdAndUser_Id(anyLong(), anyLong()))
                .thenAnswer(inv -> participantTable.stream()
                        .filter(p -> p.getChatRoom().getId().equals(inv.getArgument(0, Long.class)))
                        .filter(p -> p.getUser().getId().equals(inv.getArgument(1, Long.class)))
                        .findFirst());

        when(chatParticipantRepository.existsByChatRoom_IdAndUser_IdAndStatus(
                anyLong(), anyLong(), any(ParticipantStatus.class)))
                .thenAnswer(inv -> participantTable.stream().anyMatch(p ->
                        p.getChatRoom().getId().equals(inv.getArgument(0, Long.class))
                                && p.getUser().getId().equals(inv.getArgument(1, Long.class))
                                && p.getStatus() == inv.getArgument(2, ParticipantStatus.class)));

        when(chatRoomRepository.findActiveRoomsByUserId(anyLong()))
                .thenAnswer(inv -> participantTable.stream()
                        .filter(p -> p.getUser().getId().equals(inv.getArgument(0, Long.class)))
                        .filter(p -> p.getStatus() == ParticipantStatus.ACTIVE)
                        .map(ChatParticipant::getChatRoom)
                        .distinct()
                        .collect(Collectors.toList()));

        when(chatRoomRepository.findById(anyLong()))
                .thenAnswer(inv -> participantTable.stream()
                        .map(ChatParticipant::getChatRoom)
                        .filter(r -> r.getId().equals(inv.getArgument(0, Long.class)))
                        .findFirst());

        // save()는 실제 @CreationTimestamp처럼 createdAt을 채워준다. 단, 벽시계(now()) 대신
        // 호출할 때마다 1초씩 증가하는 가상 시계를 써서, 같은 테스트 안에서 여러 메시지를 빠르게
        // 저장해도 시각이 절대 겹치지 않게 한다 (겹치면 "커트라인 이후" 여부를 나노초 단위
        // 타이밍에 의존하게 되어 테스트가 불안정해짐).
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            if (m.getCreatedAt() == null) {
                ReflectionTestUtils.setField(m, "createdAt", clockBase.plusSeconds(messageClock.incrementAndGet()));
            }
            messageTable.add(m);
            return m;
        });

        when(messageRepository.findByChatRoom_IdOrderByCreatedAtDesc(anyLong(), any(Pageable.class)))
                .thenAnswer(inv -> messagesSince(inv.getArgument(0, Long.class), null));

        when(messageRepository.findByChatRoom_IdAndCreatedAtAfterOrderByCreatedAtDesc(
                anyLong(), any(LocalDateTime.class), any(Pageable.class)))
                .thenAnswer(inv -> messagesSince(inv.getArgument(0, Long.class), inv.getArgument(1, LocalDateTime.class)));

        when(messageRepository.findTopByChatRoom_IdOrderByCreatedAtDesc(anyLong()))
                .thenAnswer(inv -> latestMessage(inv.getArgument(0, Long.class), null));

        when(messageRepository.findTopByChatRoom_IdAndCreatedAtAfterOrderByCreatedAtDesc(
                anyLong(), any(LocalDateTime.class)))
                .thenAnswer(inv -> latestMessage(inv.getArgument(0, Long.class), inv.getArgument(1, LocalDateTime.class)));

        when(messageRepository.countByChatRoom_IdAndSender_IdNotAndIsReadFalse(anyLong(), anyLong()))
                .thenAnswer(inv -> unreadCount(inv.getArgument(0, Long.class), inv.getArgument(1, Long.class), null));

        when(messageRepository.countByChatRoom_IdAndSender_IdNotAndIsReadFalseAndCreatedAtAfter(
                anyLong(), anyLong(), any(LocalDateTime.class)))
                .thenAnswer(inv -> unreadCount(
                        inv.getArgument(0, Long.class), inv.getArgument(1, Long.class), inv.getArgument(2, LocalDateTime.class)));
    }

    private Slice<Message> messagesSince(Long roomId, LocalDateTime cutoff) {
        List<Message> list = messageTable.stream()
                .filter(m -> m.getChatRoom().getId().equals(roomId))
                .filter(m -> cutoff == null || m.getCreatedAt().isAfter(cutoff))
                .sorted(Comparator.comparing(Message::getCreatedAt).reversed())
                .collect(Collectors.toList());
        return new SliceImpl<>(list);
    }

    private Optional<Message> latestMessage(Long roomId, LocalDateTime cutoff) {
        return messageTable.stream()
                .filter(m -> m.getChatRoom().getId().equals(roomId))
                .filter(m -> cutoff == null || m.getCreatedAt().isAfter(cutoff))
                .max(Comparator.comparing(Message::getCreatedAt));
    }

    private long unreadCount(Long roomId, Long excludeSenderId, LocalDateTime cutoff) {
        return messageTable.stream()
                .filter(m -> m.getChatRoom().getId().equals(roomId))
                .filter(m -> m.getSender() == null || !m.getSender().getId().equals(excludeSenderId))
                .filter(m -> !m.isRead())
                .filter(m -> cutoff == null || m.getCreatedAt().isAfter(cutoff))
                .count();
    }

    // ── 픽스처 헬퍼 ──────────────────────────────────────────────────────────

    private User user(long id, String nickname) {
        User user = User.builder()
                .authProvider(AuthProvider.KAKAO)
                .providerId("provider-" + id)
                .status(Status.ACTIVE)
                .onboardingCompleted(true)
                .build();
        user.updateUserProfile(nickname, null);
        ReflectionTestUtils.setField(user, "id", id);
        userTable.put(id, user);
        return user;
    }

    private ChatRoom room(long id, ChatRoomType type) {
        ChatRoom room = ChatRoom.builder().type(type).build();
        ReflectionTestUtils.setField(room, "id", id);
        return room;
    }

    private ChatParticipant participant(ChatRoom room, User user, ParticipantStatus status) {
        ChatParticipant participant = ChatParticipant.builder()
                .chatRoom(room).user(user).role(ParticipantRole.MEMBER).status(status).build();
        participantTable.add(participant);
        return participant;
    }

    private Message seedMessage(ChatRoom room, User sender, String content) {
        return messageRepository.save(Message.builder()
                .chatRoom(room).sender(sender).type(MessageType.USER).content(content).build());
    }

    // ── 1) 탈퇴자 본인: 목록에서 사라져야 한다 ──────────────────────────────────

    @Test
    void 탈퇴하면_본인의_채팅방_목록에서_해당_방이_즉시_사라진다() {
        // given: withdrawer와 other가 1:1로 대화 중인 방
        User withdrawer = user(1L, "탈퇴자");
        User other = user(2L, "상대방");
        ChatRoom room = room(100L, ChatRoomType.DIRECT);
        participant(room, withdrawer, ParticipantStatus.ACTIVE);
        participant(room, other, ParticipantStatus.ACTIVE);

        // when: withdrawer가 탈퇴 처리됨 (UserServiceImpl.withdrawUser가 호출하는 것과 동일한 진입점)
        chatService.handleUserWithdrawn(1L);

        // then: 본인 목록 조회에는 이 방이 더 이상 나오지 않는다
        assertThat(chatService.getMyRooms(1L)).isEmpty();
    }

    @Test
    void 재가입만으로는_탈퇴_이전_채팅방이_다시_보이지_않는다() {
        // 이번 작업의 발단이 된 버그: User row는 삭제되지 않고 재가입 시 같은 계정을 재활성화하므로,
        // 참여 상태를 안 바꾸면 재가입 직후 예전 채팅방이 목록에 그대로 나타났었다.
        User withdrawer = user(1L, "탈퇴자");
        User other = user(2L, "상대방");
        ChatRoom room = room(100L, ChatRoomType.DIRECT);
        participant(room, withdrawer, ParticipantStatus.ACTIVE);
        participant(room, other, ParticipantStatus.ACTIVE);

        chatService.handleUserWithdrawn(1L);

        // when: 재가입 (User.reactivate()는 User 필드만 초기화하고 채팅 참여 기록은 건드리지 않음)
        withdrawer.reactivate();

        // then: 아무 것도 안 했는데 채팅방이 다시 보이면 안 된다
        assertThat(chatService.getMyRooms(1L)).isEmpty();
    }

    @Test
    void 탈퇴자_본인은_더이상_그_방의_참여자가_아니라_메시지를_보낼_수_없다() {
        User withdrawer = user(1L, "탈퇴자");
        User other = user(2L, "상대방");
        ChatRoom room = room(100L, ChatRoomType.DIRECT);
        participant(room, withdrawer, ParticipantStatus.ACTIVE);
        participant(room, other, ParticipantStatus.ACTIVE);

        chatService.handleUserWithdrawn(1L);

        // 유효한 토큰이 아직 살아있어 요청 자체는 들어오더라도, 참여자 상태가 LEFT라 거부되어야 한다.
        assertThatThrownBy(() -> chatService.saveMessage(100L, 1L, "나 아직 안 나갔어"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_CHAT_PARTICIPANT);
    }

    // ── 2) 상대방: 방/이력은 유지, 전송만 차단 ──────────────────────────────────

    @Test
    void 탈퇴해도_상대방의_채팅방_목록에는_그대로_남는다() {
        User withdrawer = user(1L, "탈퇴자");
        User other = user(2L, "상대방");
        ChatRoom room = room(100L, ChatRoomType.DIRECT);
        participant(room, withdrawer, ParticipantStatus.ACTIVE);
        participant(room, other, ParticipantStatus.ACTIVE);

        chatService.handleUserWithdrawn(1L);

        List<ChatRoomResponseDto> rooms = chatService.getMyRooms(2L);
        assertThat(rooms).hasSize(1);
        assertThat(rooms.get(0).getId()).isEqualTo(100L);
    }

    @Test
    void 탈퇴해도_상대방은_기존_대화_전체를_그대로_조회할_수_있다() {
        User withdrawer = user(1L, "탈퇴자");
        User other = user(2L, "상대방");
        ChatRoom room = room(100L, ChatRoomType.DIRECT);
        participant(room, withdrawer, ParticipantStatus.ACTIVE);
        participant(room, other, ParticipantStatus.ACTIVE);
        seedMessage(room, withdrawer, "안녕");
        seedMessage(room, other, "반가워");

        chatService.handleUserWithdrawn(1L);

        ChatMessagesResponseDto result = chatService.getMessages(100L, 2L, Pageable.unpaged());
        List<String> contents = result.getMessages().getContent().stream()
                .map(ChatMessageDto::getContent).toList();

        // 탈퇴 이전 대화 2건 + 탈퇴 시스템 메시지까지, 상대방에게는 아무것도 가려지지 않는다
        assertThat(contents).contains("안녕", "반가워");
    }

    @Test
    void 탈퇴하면_1대1_채팅방에_탈퇴_시스템_메시지가_남고_상대방에게_브로드캐스트된다() {
        User withdrawer = user(1L, "탈퇴자");
        User other = user(2L, "상대방");
        ChatRoom room = room(100L, ChatRoomType.DIRECT);
        participant(room, withdrawer, ParticipantStatus.ACTIVE);
        participant(room, other, ParticipantStatus.ACTIVE);

        chatService.handleUserWithdrawn(1L);

        assertThat(messageTable).anyMatch(m ->
                m.getType() == MessageType.SYSTEM_WITHDRAWN && m.getContent().contains("탈퇴자"));
        verify(messagingTemplate).convertAndSend(eq("/sub/chat/room/100"), any(Object.class));
    }

    @Test
    void 상대방이_탈퇴한_사람에게_메시지를_보내려_하면_탈퇴_사유로_정확히_차단된다() {
        User withdrawer = user(1L, "탈퇴자");
        User other = user(2L, "상대방");
        ChatRoom room = room(100L, ChatRoomType.DIRECT);
        participant(room, withdrawer, ParticipantStatus.ACTIVE);
        participant(room, other, ParticipantStatus.ACTIVE);

        // 실제 탈퇴 흐름과 동일하게: 채팅 처리 → User.withdraw()로 상태 전환
        chatService.handleUserWithdrawn(1L);
        withdrawer.withdraw();

        // 차단 자체는 이미 기존 로직(assertOtherParticipantNotWithdrawn)이 하고 있었지만,
        // 이번에 그 체크가 "ACTIVE 참여자만" 보도록 바뀌면 안 되는 걸 확인하는 회귀 테스트이기도 하다.
        // (참여자 status를 LEFT로 바꾸는 변경과 맞물려 자칫 OTHER_USER_LEFT로 뒤바뀔 수 있었음)
        assertThatThrownBy(() -> chatService.saveMessage(100L, 2L, "아직 거기 있어?"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OTHER_USER_WITHDRAWN);
    }

    // ── 3) 재가입 후 대화 재개: 본인에게는 이전 이력이 감춰지고, 상대방에게는 그대로 ───────

    @Test
    void 재가입후_대화를_재개해도_재가입한_본인에게는_탈퇴_이전_이력이_보이지_않는다() {
        User withdrawer = user(1L, "탈퇴자");
        User other = user(2L, "상대방");
        ChatRoom room = room(100L, ChatRoomType.DIRECT);
        ChatParticipant myParticipant = participant(room, withdrawer, ParticipantStatus.ACTIVE);
        participant(room, other, ParticipantStatus.ACTIVE);
        seedMessage(room, withdrawer, "탈퇴전_대화");

        chatService.handleUserWithdrawn(1L);
        withdrawer.reactivate();

        // "같은 상대와 대화 재개"의 핵심 효과만 재현한다: startDirectChat()이 내부적으로 호출하는
        // 것과 동일한 rejoin(). startDirectChat 자체(EntityManager/TransactionTemplate로 방을
        // 찾고 만드는 부분)는 이번 변경과 무관해 여기서 다시 검증하지 않는다.
        myParticipant.rejoin();

        ChatMessagesResponseDto result = chatService.getMessages(100L, 1L, Pageable.unpaged());
        List<String> contents = result.getMessages().getContent().stream()
                .map(ChatMessageDto::getContent).toList();

        assertThat(contents).doesNotContain("탈퇴전_대화");
    }

    @Test
    void 재가입후_대화를_재개해도_상대방에게는_탈퇴_이전_이력이_그대로_보인다() {
        User withdrawer = user(1L, "탈퇴자");
        User other = user(2L, "상대방");
        ChatRoom room = room(100L, ChatRoomType.DIRECT);
        ChatParticipant myParticipant = participant(room, withdrawer, ParticipantStatus.ACTIVE);
        participant(room, other, ParticipantStatus.ACTIVE);
        seedMessage(room, withdrawer, "탈퇴전_대화");

        chatService.handleUserWithdrawn(1L);
        withdrawer.reactivate();
        myParticipant.rejoin();

        ChatMessagesResponseDto result = chatService.getMessages(100L, 2L, Pageable.unpaged());
        List<String> contents = result.getMessages().getContent().stream()
                .map(ChatMessageDto::getContent).toList();

        assertThat(contents).contains("탈퇴전_대화");
    }

    @Test
    void 재가입후_목록_미리보기에는_탈퇴_이전_마지막_메시지가_노출되지_않는다() {
        User withdrawer = user(1L, "탈퇴자");
        User other = user(2L, "상대방");
        ChatRoom room = room(100L, ChatRoomType.DIRECT);
        ChatParticipant myParticipant = participant(room, withdrawer, ParticipantStatus.ACTIVE);
        participant(room, other, ParticipantStatus.ACTIVE);
        seedMessage(room, withdrawer, "탈퇴전_마지막_메시지");

        chatService.handleUserWithdrawn(1L);
        withdrawer.reactivate();
        myParticipant.rejoin();

        // 재개 직후에는 새 메시지가 아직 없으므로, 목록의 미리보기는 "메시지 없음" 상태여야 한다.
        // (신규 채팅방 생성 직후와 동일한, 프론트가 이미 처리하고 있어야 하는 상태)
        List<ChatRoomResponseDto> rooms = chatService.getMyRooms(1L);
        assertThat(rooms).hasSize(1);
        assertThat(rooms.get(0).getLastMessageContent()).isNull();
        assertThat(rooms.get(0).getUnreadCount()).isZero();
    }

    @Test
    void 상대방의_목록_미리보기는_탈퇴로_인해_비워지지_않는다() {
        User withdrawer = user(1L, "탈퇴자");
        User other = user(2L, "상대방");
        ChatRoom room = room(100L, ChatRoomType.DIRECT);
        participant(room, withdrawer, ParticipantStatus.ACTIVE);
        participant(room, other, ParticipantStatus.ACTIVE);
        seedMessage(room, withdrawer, "탈퇴전_마지막_메시지");

        List<ChatRoomResponseDto> before = chatService.getMyRooms(2L);
        assertThat(before.get(0).getLastMessageContent()).isEqualTo("탈퇴전_마지막_메시지");

        chatService.handleUserWithdrawn(1L);

        // 탈퇴 시스템 메시지도 실제 Message row라서 시간상 새 마지막 메시지가 되는 게 맞다
        // (카카오톡/라인의 "OO님이 나갔습니다" 미리보기와 동일한 정상 동작). 재가입 유저 쪽처럼
        // messageVisibleFrom 커트라인에 걸려 미리보기 자체가 null로 통째 비워지지는 않아야 한다.
        List<ChatRoomResponseDto> after = chatService.getMyRooms(2L);
        assertThat(after.get(0).getLastMessageContent()).isNotNull();
        assertThat(after.get(0).getUnreadCount()).isEqualTo(2); // 탈퇴전 메시지 + 탈퇴 시스템 메시지 모두 안읽음 집계
    }

    // ── 4) 범위 확인: GROUP 채팅방은 이번 변경의 영향을 받지 않는다 ───────────────

    @Test
    void GROUP_채팅방_참여자는_탈퇴_처리로_인해_상태가_바뀌지_않는다() {
        User withdrawer = user(1L, "탈퇴자");
        ChatRoom groupRoom = room(200L, ChatRoomType.GROUP);
        ChatParticipant participant = participant(groupRoom, withdrawer, ParticipantStatus.ACTIVE);

        chatService.handleUserWithdrawn(1L);

        // DIRECT만 처리 대상이므로, GROUP 참여자는 상태도 커트라인도 그대로여야 한다
        assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.ACTIVE);
        assertThat(participant.getMessageVisibleFrom()).isNull();
        assertThat(messageTable).isEmpty();
    }
}
