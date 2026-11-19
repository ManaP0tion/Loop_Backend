package com.loop.loop_backend.CompanionHeart.repository;

import com.loop.loop_backend.CompanionHeart.domain.CompanionHeart;
import com.loop.loop_backend.CompanionPost.domain.CompanionActivity;
import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// 관심 프로필 전체보기 요구사항(v2): 한 공연에서 내가 하트한 프로필을 최근 하트한 순으로 가져온다(day로 묶어도 day 안은 최근 하트순).
// 다른 공연의 하트, 다른 사람의 하트는 섞이지 않는다.
@DataJpaTest
class CompanionHeartRecentOrderTest {

    @Autowired private CompanionHeartRepository companionHeartRepository;
    @Autowired private EntityManager em;

    private User user(String providerId) {
        User user = User.builder().authProvider(AuthProvider.KAKAO).providerId(providerId).build();
        em.persist(user);
        return user;
    }

    private Concert concert() {
        Concert concert = Concert.builder().title("페스티벌").category(ConcertCategory.FESTIVAL).build();
        em.persist(concert);
        return concert;
    }

    private CompanionPost post(User author, Concert concert) {
        CompanionPost post = CompanionPost.builder().user(author).concert(concert).watchDay(WatchDay.DAY1)
                .activities(Set.of(CompanionActivity.MEAL)).build();
        em.persist(post);
        return post;
    }

    // 하트 시각은 저장 시 자동으로 찍혀서(@CreationTimestamp) 테스트에서는 저장 후 직접 바꾼다
    private void heart(User viewer, CompanionPost post, LocalDateTime heartedAt) {
        CompanionHeart heart = CompanionHeart.builder().user(viewer).companionPost(post).build();
        em.persist(heart);
        em.flush();
        em.createNativeQuery("UPDATE companion_hearts SET created_at = :at WHERE id = :id")
                .setParameter("at", heartedAt).setParameter("id", heart.getId()).executeUpdate();
    }

    @Test
    @DisplayName("한 공연에서 내가 하트한 프로필을 최근 하트한 순으로 가져온다")
    void findByConcert_ReturnsMostRecentHeartFirst() {
        // given
        User me = user("me");
        User other = user("other");
        Concert concert = concert();
        Concert anotherConcert = concert();
        CompanionPost first = post(user("a1"), concert);
        CompanionPost second = post(user("a2"), concert);
        CompanionPost third = post(user("a3"), concert);
        LocalDateTime base = LocalDateTime.of(2026, 11, 1, 12, 0);
        heart(me, second, base.plusHours(2));
        heart(me, first, base);
        heart(me, third, base.plusHours(1));
        heart(other, first, base.plusHours(5));                      // 다른 사람의 하트
        heart(me, post(user("a4"), anotherConcert), base.plusHours(9)); // 다른 공연의 하트
        em.clear();

        // when & then
        assertThat(companionHeartRepository
                .findAllByUser_IdAndCompanionPost_Concert_IdOrderByCreatedAtDesc(me.getId(), concert.getId()))
                .extracting(heart -> heart.getCompanionPost().getId())
                .containsExactly(second.getId(), third.getId(), first.getId());
    }
}
