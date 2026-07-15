package com.loop.loop_backend.CompanionPost.repository;

import com.loop.loop_backend.CompanionPost.domain.CompanionActivity;
import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.User.domain.AgeGroup;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Gender;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CompanionPostRepositoryTest {

    @Autowired
    private CompanionPostRepository companionPostRepository;

    @Autowired
    private EntityManager entityManager;

    private User persistUser(String providerId, Gender gender, int age) {
        User user = User.builder()
                .authProvider(AuthProvider.KAKAO)
                .providerId(providerId)
                .status(Status.ACTIVE)
                .onboardingCompleted(true)
                .build();
        String nickname = providerId.substring(0, Math.min(5, providerId.length()));
        user.completeOnboarding(nickname, LocalDate.now().minusYears(age), gender);
        entityManager.persist(user);
        return user;
    }

    private void persistCompanionPost(User owner) {
        entityManager.persist(CompanionPost.builder()
                .user(owner)
                .concertId(10L)
                .watchDay(WatchDay.DAY1)
                .activities(Set.of(CompanionActivity.CONCERT))
                .build());
    }

    @Test
    void 성별_조건으로_필터링된다() {
        User male = persistUser("male", Gender.MALE, 25);
        User female = persistUser("female", Gender.FEMALE, 25);
        persistCompanionPost(male);
        persistCompanionPost(female);
        entityManager.flush();

        Specification<CompanionPost> spec = Specification.allOf(
                CompanionPostSpecifications.concertIdEquals(10L),
                CompanionPostSpecifications.watchDayEquals(WatchDay.DAY1),
                CompanionPostSpecifications.genderEquals(Gender.MALE));

        List<CompanionPost> result = companionPostRepository.findAll(spec);

        assertThat(result).extracting(post -> post.getUser().getProviderId())
                .containsExactly("male");
    }

    @Test
    void 나이대_조건으로_필터링된다() {
        User twenties = persistUser("twenties", Gender.MALE, 22);
        User thirties = persistUser("thirties", Gender.MALE, 32);
        persistCompanionPost(twenties);
        persistCompanionPost(thirties);
        entityManager.flush();

        Specification<CompanionPost> spec = Specification.allOf(
                CompanionPostSpecifications.concertIdEquals(10L),
                CompanionPostSpecifications.watchDayEquals(WatchDay.DAY1),
                CompanionPostSpecifications.ageGroupIn(List.of(AgeGroup.THIRTY_TO_THIRTY_FOUR)));

        List<CompanionPost> result = companionPostRepository.findAll(spec);

        assertThat(result).extracting(post -> post.getUser().getProviderId())
                .containsExactly("thirties");
    }

    @Test
    void 본인_프로필은_후보에서_제외된다() {
        User me = persistUser("me", Gender.MALE, 25);
        User other = persistUser("other", Gender.MALE, 25);
        persistCompanionPost(me);
        persistCompanionPost(other);
        entityManager.flush();

        Specification<CompanionPost> spec = Specification.allOf(
                CompanionPostSpecifications.concertIdEquals(10L),
                CompanionPostSpecifications.watchDayEquals(WatchDay.DAY1),
                CompanionPostSpecifications.userIdNotEquals(me.getId()));

        List<CompanionPost> result = companionPostRepository.findAll(spec);

        assertThat(result).extracting(post -> post.getUser().getProviderId())
                .containsExactly("other");
    }

    @Test
    void ANY가_포함되면_나이대_필터링을_하지_않는다() {
        User twenties = persistUser("twenties2", Gender.MALE, 22);
        User thirties = persistUser("thirties2", Gender.MALE, 32);
        persistCompanionPost(twenties);
        persistCompanionPost(thirties);
        entityManager.flush();

        Specification<CompanionPost> spec = Specification.allOf(
                CompanionPostSpecifications.concertIdEquals(10L),
                CompanionPostSpecifications.watchDayEquals(WatchDay.DAY1),
                CompanionPostSpecifications.ageGroupIn(List.of(AgeGroup.ANY)));

        List<CompanionPost> result = companionPostRepository.findAll(spec);

        assertThat(result).extracting(post -> post.getUser().getProviderId())
                .containsExactlyInAnyOrder("twenties2", "thirties2");
    }
}