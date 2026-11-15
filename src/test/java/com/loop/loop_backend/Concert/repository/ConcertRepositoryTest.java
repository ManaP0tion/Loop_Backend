package com.loop.loop_backend.Concert.repository;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.CompanionPost.domain.CompanionActivity;
import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
import com.loop.loop_backend.CompanionPost.domain.WatchDay;
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
class ConcertRepositoryTest {

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private EntityManager entityManager;

    private Concert persistConcert(String title, LocalDate startDate, LocalDate endDate) {
        Concert concert = Concert.builder()
                .title(title)
                .category(ConcertCategory.DOMESTIC_ARTIST)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        entityManager.persist(concert);
        return concert;
    }

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

    // 콘서트 삭제 -> companion_posts(ON DELETE CASCADE)까지는 됐지만, companion_post_activities에
    // cascade가 없어서 FK 위반으로 삭제 자체가 실패하던 버그의 회귀 테스트.
    @Test
    void 콘서트를_삭제하면_동행글과_activities_컬렉션까지_함께_삭제된다() {
        User user = persistUser("cUser");
        Concert concert = persistConcert("삭제될 공연", LocalDate.now().plusDays(1), LocalDate.now().plusDays(1));

        CompanionPost post = CompanionPost.builder()
                .user(user)
                .concert(concert)
                .watchDay(WatchDay.DAY1)
                .activities(Set.of(CompanionActivity.MEAL, CompanionActivity.PHOTO))
                .build();
        entityManager.persist(post);
        entityManager.flush();
        Long postId = post.getId();

        // 예전엔 이 flush()에서 companion_post_activities FK 위반(ConstraintViolationException)이 발생했다.
        concertRepository.delete(concert);
        entityManager.flush();

        assertThat(concertRepository.findById(concert.getId())).isEmpty();

        Number activityCount = (Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM companion_post_activities WHERE companion_post_id = :postId")
                .setParameter("postId", postId)
                .getSingleResult();
        assertThat(activityCount.longValue()).isZero();
    }

    @Test
    void 시작일은_지났지만_종료일이_남은_콘서트는_목록에_포함된다() {
        LocalDate today = LocalDate.now();
        persistConcert("연장 공연", today.minusDays(2), today.plusDays(1));
        entityManager.flush();

        List<Concert> result = concertRepository.findUpcomingOrUndated(today);

        assertThat(result).extracting(Concert::getTitle).containsExactly("연장 공연");
    }

    @Test
    void 종료일까지_지난_콘서트는_목록에서_제외된다() {
        LocalDate today = LocalDate.now();
        persistConcert("종료된 공연", today.minusDays(5), today.minusDays(1));
        entityManager.flush();

        List<Concert> result = concertRepository.findUpcomingOrUndated(today);

        assertThat(result).isEmpty();
    }

    @Test
    void 종료일이_없으면_시작일을_기준으로_판단한다() {
        LocalDate today = LocalDate.now();
        persistConcert("종료일_없음_미래", today.plusDays(1), null);
        persistConcert("종료일_없음_과거", today.minusDays(1), null);
        entityManager.flush();

        List<Concert> result = concertRepository.findUpcomingOrUndated(today);

        assertThat(result).extracting(Concert::getTitle).containsExactly("종료일_없음_미래");
    }

    @Test
    void 시작일_종료일_모두_없으면_날짜_미정으로_항상_포함된다() {
        LocalDate today = LocalDate.now();
        persistConcert("날짜_미정", null, null);
        entityManager.flush();

        List<Concert> result = concertRepository.findUpcomingOrUndated(today);

        assertThat(result).extracting(Concert::getTitle).containsExactly("날짜_미정");
    }
}