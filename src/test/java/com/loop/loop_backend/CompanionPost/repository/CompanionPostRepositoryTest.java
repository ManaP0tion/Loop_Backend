package com.loop.loop_backend.CompanionPost.repository;

import com.loop.loop_backend.Block.domain.Block;
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
        persistCompanionPost(owner, Set.of(CompanionActivity.CONCERT));
    }

    private void persistCompanionPost(User owner, Set<CompanionActivity> activities) {
        persistCompanionPost(owner, activities, false);
    }

    private void persistCompanionPost(User owner, Set<CompanionActivity> activities, boolean sameGenderOnly) {
        entityManager.persist(CompanionPost.builder()
                .user(owner)
                .concertId(10L)
                .watchDay(WatchDay.DAY1)
                .activities(activities)
                .sameGenderOnly(sameGenderOnly)
                .build());
    }

    @Test
    void 공연관람을_선택하지_않은_프로필만_조회된다() {
        User watcher = persistUser("watch", Gender.MALE, 25);
        User nonWatcher = persistUser("non", Gender.MALE, 25);
        persistCompanionPost(watcher, Set.of(CompanionActivity.CONCERT));
        persistCompanionPost(nonWatcher, Set.of(CompanionActivity.MEAL, CompanionActivity.PHOTO));
        entityManager.flush();

        Specification<CompanionPost> spec = Specification.allOf(
                CompanionPostSpecifications.concertIdEquals(10L),
                CompanionPostSpecifications.watchDayEquals(WatchDay.DAY1),
                CompanionPostSpecifications.doesNotHaveActivity(CompanionActivity.CONCERT));

        List<CompanionPost> result = companionPostRepository.findAll(spec);

        assertThat(result).extracting(post -> post.getUser().getProviderId())
                .containsExactly("non");
    }

    @Test
    void sameGenderOnly인_프로필은_이성_조회자에게_숨겨진다() {
        User sameGenderOnlyMale = persistUser("sgo", Gender.MALE, 25);
        User openFemale = persistUser("open", Gender.FEMALE, 25);
        persistCompanionPost(sameGenderOnlyMale, Set.of(CompanionActivity.CONCERT), true);
        persistCompanionPost(openFemale, Set.of(CompanionActivity.CONCERT), false);
        entityManager.flush();

        Specification<CompanionPost> spec = Specification.allOf(
                CompanionPostSpecifications.concertIdEquals(10L),
                CompanionPostSpecifications.watchDayEquals(WatchDay.DAY1),
                CompanionPostSpecifications.respectsSameGenderOnly(Gender.FEMALE));

        List<CompanionPost> result = companionPostRepository.findAll(spec);

        assertThat(result).extracting(post -> post.getUser().getProviderId())
                .containsExactly("open");
    }

    @Test
    void sameGenderOnly인_프로필도_동성_조회자에게는_보인다() {
        User sameGenderOnlyMale = persistUser("sgo2", Gender.MALE, 25);
        persistCompanionPost(sameGenderOnlyMale, Set.of(CompanionActivity.CONCERT), true);
        entityManager.flush();

        Specification<CompanionPost> spec = Specification.allOf(
                CompanionPostSpecifications.concertIdEquals(10L),
                CompanionPostSpecifications.watchDayEquals(WatchDay.DAY1),
                CompanionPostSpecifications.respectsSameGenderOnly(Gender.MALE));

        List<CompanionPost> result = companionPostRepository.findAll(spec);

        assertThat(result).extracting(post -> post.getUser().getProviderId())
                .containsExactly("sgo2");
    }

    @Test
    void 비공개_프로필은_목록에서_제외된다() {
        User visibleUser = persistUser("show", Gender.MALE, 25);
        User hiddenUser = persistUser("hide", Gender.MALE, 25);
        persistCompanionPost(visibleUser);

        CompanionPost hiddenPost = CompanionPost.builder()
                .user(hiddenUser)
                .concertId(10L)
                .watchDay(WatchDay.DAY1)
                .activities(Set.of(CompanionActivity.CONCERT))
                .build();
        hiddenPost.toggleVisible(false);
        entityManager.persist(hiddenPost);
        entityManager.flush();

        Specification<CompanionPost> spec = Specification.allOf(
                CompanionPostSpecifications.concertIdEquals(10L),
                CompanionPostSpecifications.watchDayEquals(WatchDay.DAY1),
                CompanionPostSpecifications.isVisible());

        List<CompanionPost> result = companionPostRepository.findAll(spec);

        assertThat(result).extracting(post -> post.getUser().getProviderId())
                .containsExactly("show");
    }

    @Test
    void 내가_차단한_사용자의_프로필은_제외된다() {
        User me = persistUser("me2", Gender.MALE, 25);
        User blockedByMe = persistUser("blkme", Gender.MALE, 25);
        User stranger = persistUser("stranger", Gender.MALE, 25);
        persistCompanionPost(blockedByMe);
        persistCompanionPost(stranger);
        entityManager.persist(Block.builder().blocker(me).blocked(blockedByMe).build());
        entityManager.flush();

        Specification<CompanionPost> spec = Specification.allOf(
                CompanionPostSpecifications.concertIdEquals(10L),
                CompanionPostSpecifications.watchDayEquals(WatchDay.DAY1),
                CompanionPostSpecifications.hasNoBlockRelationWith(me.getId()));

        List<CompanionPost> result = companionPostRepository.findAll(spec);

        assertThat(result).extracting(post -> post.getUser().getProviderId())
                .containsExactly("stranger");
    }

    @Test
    void 나를_차단한_사용자의_프로필도_제외된다() {
        User me = persistUser("me3", Gender.MALE, 25);
        User blockedMe = persistUser("blkedme", Gender.MALE, 25);
        User stranger = persistUser("stranger2", Gender.MALE, 25);
        persistCompanionPost(blockedMe);
        persistCompanionPost(stranger);
        entityManager.persist(Block.builder().blocker(blockedMe).blocked(me).build());
        entityManager.flush();

        Specification<CompanionPost> spec = Specification.allOf(
                CompanionPostSpecifications.concertIdEquals(10L),
                CompanionPostSpecifications.watchDayEquals(WatchDay.DAY1),
                CompanionPostSpecifications.hasNoBlockRelationWith(me.getId()));

        List<CompanionPost> result = companionPostRepository.findAll(spec);

        assertThat(result).extracting(post -> post.getUser().getProviderId())
                .containsExactly("stranger2");
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
                CompanionPostSpecifications.authorGenderEquals(Gender.MALE));

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
    void 나이대_다중선택시_선택되지_않은_중간_구간은_제외된다() {
        User twenties = persistUser("t25", Gender.MALE, 27);
        User thirties = persistUser("t32", Gender.MALE, 32);
        User late30s = persistUser("t37", Gender.MALE, 37);
        persistCompanionPost(twenties);
        persistCompanionPost(thirties);
        persistCompanionPost(late30s);
        entityManager.flush();

        Specification<CompanionPost> spec = Specification.allOf(
                CompanionPostSpecifications.concertIdEquals(10L),
                CompanionPostSpecifications.watchDayEquals(WatchDay.DAY1),
                CompanionPostSpecifications.ageGroupIn(
                        List.of(AgeGroup.TWENTY_FIVE_TO_TWENTY_NINE, AgeGroup.THIRTY_FIVE_TO_THIRTY_NINE)));

        List<CompanionPost> result = companionPostRepository.findAll(spec);

        assertThat(result).extracting(post -> post.getUser().getProviderId())
                .containsExactlyInAnyOrder("t25", "t37");
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