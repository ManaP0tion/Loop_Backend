package com.loop.loop_backend.Chat.repository;

import com.loop.loop_backend.Chat.domain.ChatRoom;
import com.loop.loop_backend.Chat.domain.ChatRoomType;
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
        user.completeOnboarding(providerId, LocalDate.now().minusYears(25), Gender.MALE, providerId + "@test.com");
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
}