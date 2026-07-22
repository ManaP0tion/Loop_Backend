package com.loop.loop_backend.Chat.repository;

import com.loop.loop_backend.Chat.domain.ChatParticipant;
import com.loop.loop_backend.Chat.domain.ChatRoom;
import com.loop.loop_backend.Chat.domain.ChatRoomType;
import com.loop.loop_backend.Chat.domain.ParticipantRole;
import com.loop.loop_backend.Chat.domain.ParticipantStatus;
import com.loop.loop_backend.Chat.dto.ChatRoomSummaryDto;
import com.loop.loop_backend.CompanionPost.domain.CompanionActivity;
import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.CompanionPost.repository.CompanionPostRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Gender;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ChatRoomRepositoryTest {

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private CompanionPostRepository companionPostRepository;

    @Autowired
    private EntityManager entityManager;

    private User persistUser(String providerId) {
        User user = User.builder()
                .authProvider(AuthProvider.KAKAO)
                .providerId(providerId)
                .status(Status.ACTIVE)
                .onboardingCompleted(true)
                .build();
        user.completeOnboarding(providerId, LocalDate.now().minusYears(25), Gender.MALE);
        entityManager.persist(user);
        return user;
    }

    @Test
    void 동행글이_삭제되면_연결된_채팅방은_유지되고_참조만_끊긴다() {
        // given: 동행글과 그 동행글로 만들어진 GROUP 채팅방
        Concert concert = Concert.builder()
                .title("테스트 콘서트")
                .category(ConcertCategory.DOMESTIC_ARTIST)
                .build();
        entityManager.persist(concert);

        User host = persistUser("host");
        CompanionPost post = CompanionPost.builder()
                .user(host)
                .concert(concert)
                .watchDay(WatchDay.DAY1)
                .activities(Set.of(CompanionActivity.CONCERT))
                .build();
        entityManager.persist(post);

        ChatRoom room = ChatRoom.builder()
                .post(post)
                .type(ChatRoomType.GROUP)
                .name("동행 채팅방")
                .build();
        entityManager.persist(room);
        entityManager.flush();

        Long roomId = room.getId();
        Long postId = post.getId();
        // 영속성 컨텍스트를 비워서, Hibernate가 메모리에 들고 있는 room->post 참조 때문에
        // "삭제하려는 post를 아직 참조 중"이라고 오판하지 않게 함 (DB의 ON DELETE SET NULL은
        // Hibernate가 알지 못하는 순수 DB 레벨 동작이라, 세션에 남은 참조와는 별개로 처리해야 함)
        entityManager.clear();

        // when: 동행글 삭제
        companionPostRepository.deleteById(postId);
        entityManager.flush();
        entityManager.clear(); // DB가 SET NULL 처리한 값을 다시 읽어오도록 컨텍스트를 한 번 더 비움

        // then: 채팅방 자체는 남아있고, 참조하던 동행글만 null로 바뀐다
        ChatRoom found = chatRoomRepository.findById(roomId).orElseThrow();
        assertThat(found.getPost()).isNull();
    }

    @Test
    void 요약_조회는_동행글과_공연_id까지_한번에_가져온다() {
        Concert concert = Concert.builder()
                .title("테스트 콘서트")
                .category(ConcertCategory.DOMESTIC_ARTIST)
                .build();
        entityManager.persist(concert);

        User host = persistUser("sumH");
        CompanionPost post = CompanionPost.builder()
                .user(host)
                .concert(concert)
                .watchDay(WatchDay.DAY1)
                .activities(Set.of(CompanionActivity.CONCERT))
                .build();
        entityManager.persist(post);

        ChatRoom room = ChatRoom.builder()
                .post(post)
                .type(ChatRoomType.GROUP)
                .name("동행 채팅방")
                .build();
        entityManager.persist(room);
        entityManager.flush();
        entityManager.clear();

        ChatRoomSummaryDto summary = chatRoomRepository.findSummaryById(room.getId()).orElseThrow();

        assertThat(summary.id()).isEqualTo(room.getId());
        assertThat(summary.name()).isEqualTo("동행 채팅방");
        assertThat(summary.type()).isEqualTo(ChatRoomType.GROUP);
        assertThat(summary.postId()).isEqualTo(post.getId());
        assertThat(summary.concertId()).isEqualTo(concert.getId());
    }

    @Test
    void 동행글_연결이_없는_DIRECT_방의_요약은_postId와_concertId가_null이다() {
        User a = persistUser("sumA");
        User b = persistUser("sumB");
        ChatRoom room = persistDirectRoom(a, b);
        entityManager.flush();
        entityManager.clear();

        ChatRoomSummaryDto summary = chatRoomRepository.findSummaryById(room.getId()).orElseThrow();

        assertThat(summary.postId()).isNull();
        assertThat(summary.concertId()).isNull();
    }

    @Test
    void 두_유저_사이_DIRECT_방이_중복이어도_예외_없이_모두_반환된다() {
        // 과거 race로 DIRECT 방이 2개 생긴 상황 재현: Optional 반환이었다면 NonUniqueResultException.
        User a = persistUser("uA");
        User b = persistUser("uB");

        ChatRoom room1 = persistDirectRoom(a, b);
        ChatRoom room2 = persistDirectRoom(a, b);
        entityManager.flush();
        entityManager.clear();

        List<ChatRoom> anyStatus = chatRoomRepository.findDirectRoomsBetweenAnyStatus(a.getId(), b.getId());
        assertThat(anyStatus).extracting(ChatRoom::getId).containsExactly(room1.getId(), room2.getId());
    }

    private ChatRoom persistDirectRoom(User u1, User u2) {
        ChatRoom room = ChatRoom.builder().type(ChatRoomType.DIRECT).build();
        entityManager.persist(room);
        entityManager.persist(ChatParticipant.builder()
                .chatRoom(room).user(u1).role(ParticipantRole.HOST).status(ParticipantStatus.ACTIVE).build());
        entityManager.persist(ChatParticipant.builder()
                .chatRoom(room).user(u2).role(ParticipantRole.MEMBER).status(ParticipantStatus.ACTIVE).build());
        return room;
    }
}