package com.loop.loop_backend.Chat.repository;

import com.loop.loop_backend.Chat.domain.ChatParticipant;
import com.loop.loop_backend.Chat.domain.ChatRoom;
import com.loop.loop_backend.Chat.domain.ChatRoomType;
import com.loop.loop_backend.Chat.domain.Message;
import com.loop.loop_backend.Chat.domain.MessageType;
import com.loop.loop_backend.Chat.domain.ParticipantRole;
import com.loop.loop_backend.Chat.domain.ParticipantStatus;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * message_visible_from 커트라인을 위해 새로 추가한 3개의 파생 쿼리 메서드를 실제 H2 위에서 검증한다.
 * Mockito로 흉내낸 인메모리 fake(ChatServiceImplTest)는 필터링 "규칙"은 검증해주지만,
 * 파생 쿼리 메서드 이름 자체가 Spring Data가 의도한 JPQL로 정확히 변환되는지는 실제 컨텍스트를
 * 띄워봐야만 확인할 수 있어서 별도로 둔다.
 */
@DataJpaTest
class MessageRepositoryTest {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private EntityManager entityManager;

    private User persistUser(String providerId) {
        User user = User.builder()
                .authProvider(AuthProvider.KAKAO)
                .providerId(providerId)
                .status(Status.ACTIVE)
                .onboardingCompleted(true)
                .build();
        entityManager.persist(user);
        return user;
    }

    private ChatRoom persistDirectRoom(User a, User b) {
        ChatRoom room = ChatRoom.builder().type(ChatRoomType.DIRECT).build();
        entityManager.persist(room);
        entityManager.persist(ChatParticipant.builder()
                .chatRoom(room).user(a).role(ParticipantRole.HOST).status(ParticipantStatus.ACTIVE).build());
        entityManager.persist(ChatParticipant.builder()
                .chatRoom(room).user(b).role(ParticipantRole.MEMBER).status(ParticipantStatus.ACTIVE).build());
        return room;
    }

    // 저장 시각(createdAt)은 @CreationTimestamp에 @Column(updatable = false)까지 붙어있어서,
    // 리플렉션으로 자바 필드값만 바꾸고 flush()해봐야 Hibernate가 UPDATE 문에서 이 컬럼을 아예
    // 제외해버려 DB엔 반영되지 않는다(실제로 처음엔 이 방식으로 썼다가 DB엔 진짜 persist 시각이
    // 그대로 남아있어서 커트라인 필터링 테스트가 전부 깨졌었다). Hibernate 우회하고 네이티브
    // SQL로 직접 DB 컬럼을 덮어써야 실제로 반영된다.
    private Message persistMessageAt(ChatRoom room, User sender, String content, LocalDateTime createdAt) {
        Message message = Message.builder()
                .chatRoom(room).sender(sender).type(MessageType.USER).content(content).build();
        entityManager.persist(message);
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE messages SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", message.getId())
                .executeUpdate();
        entityManager.flush();
        ReflectionTestUtils.setField(message, "createdAt", createdAt);
        return message;
    }

    @Test
    void 커트라인_이후_메시지만_최신순으로_조회된다() {
        User a = persistUser("cutA");
        User b = persistUser("cutB");
        ChatRoom room = persistDirectRoom(a, b);

        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        persistMessageAt(room, a, "탈퇴_이전_1", base.minusMinutes(2));
        Message cutoffMessage = persistMessageAt(room, a, "탈퇴_시스템_메시지", base.minusMinutes(1));
        persistMessageAt(room, b, "재가입_이후_1", base.plusMinutes(1));
        persistMessageAt(room, b, "재가입_이후_2", base.plusMinutes(2));
        entityManager.clear();

        Slice<Message> result = messageRepository.findByChatRoom_IdAndCreatedAtAfterOrderByCreatedAtDesc(
                room.getId(), cutoffMessage.getCreatedAt(), PageRequest.of(0, 10));

        // 커트라인(시스템 메시지) 자신과 그 이전 메시지는 제외되고, 이후 것만 최신순으로 남는다
        assertThat(result.getContent()).extracting(Message::getContent)
                .containsExactly("재가입_이후_2", "재가입_이후_1");
    }

    @Test
    void 커트라인_이후_메시지가_없으면_마지막_메시지_조회는_비어있다() {
        User a = persistUser("topA");
        User b = persistUser("topB");
        ChatRoom room = persistDirectRoom(a, b);

        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        Message cutoffMessage = persistMessageAt(room, a, "탈퇴_시스템_메시지", base);
        entityManager.clear();

        Optional<Message> withCutoff = messageRepository
                .findTopByChatRoom_IdAndCreatedAtAfterOrderByCreatedAtDesc(room.getId(), cutoffMessage.getCreatedAt());
        assertThat(withCutoff).isEmpty();

        Message afterMessage = persistMessageAt(room, b, "재가입_이후_대화", base.plusMinutes(5));
        entityManager.clear();

        Optional<Message> afterNewMessage = messageRepository
                .findTopByChatRoom_IdAndCreatedAtAfterOrderByCreatedAtDesc(room.getId(), cutoffMessage.getCreatedAt());
        assertThat(afterNewMessage).isPresent();
        assertThat(afterNewMessage.get().getContent()).isEqualTo(afterMessage.getContent());
    }

    @Test
    void 안읽은_메시지_집계도_커트라인_이후_것만_센다() {
        User a = persistUser("cntA");
        User b = persistUser("cntB");
        ChatRoom room = persistDirectRoom(a, b);

        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        // a(=탈퇴자)가 탈퇴 전에 보낸, 아직 안읽은 메시지 2건 → 커트라인 이전이라 집계되면 안 됨
        persistMessageAt(room, a, "탈퇴전_안읽음_1", base.minusMinutes(3));
        Message cutoffMessage = persistMessageAt(room, a, "탈퇴전_안읽음_2", base.minusMinutes(2));
        // 재가입 후 b가 보낸, 안읽은 메시지 1건 → 커트라인 이후라 집계되어야 함
        persistMessageAt(room, b, "재가입후_안읽음", base.plusMinutes(1));
        entityManager.clear();

        long count = messageRepository.countByChatRoom_IdAndSender_IdNotAndIsReadFalseAndCreatedAtAfter(
                room.getId(), a.getId(), cutoffMessage.getCreatedAt());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void 커트라인_없는_기존_조회_메서드는_전체_이력을_그대로_반환한다() {
        // 회귀 방지: 새 쿼리를 추가하면서 기존(커트라인 없는) 조회 메서드의 동작이 바뀌지 않았는지 확인
        User a = persistUser("legacyA");
        User b = persistUser("legacyB");
        ChatRoom room = persistDirectRoom(a, b);

        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        persistMessageAt(room, a, "메시지1", base);
        persistMessageAt(room, b, "메시지2", base.plusMinutes(1));
        entityManager.clear();

        List<Message> all = messageRepository
                .findByChatRoom_IdOrderByCreatedAtDesc(room.getId(), PageRequest.of(0, 10))
                .getContent();
        assertThat(all).extracting(Message::getContent).containsExactly("메시지2", "메시지1");
    }
}
