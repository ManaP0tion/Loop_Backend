package com.loop.loop_backend.CompanionPost.repository;

import com.loop.loop_backend.Block.domain.Block;
import com.loop.loop_backend.CompanionPost.domain.CompanionActivity;
import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.CompanionPost.dto.ConcertReminderRow;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.User.domain.AgeGroup;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Gender;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.common.time.ExpiryCutoff;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
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

    private Concert concert;

    @BeforeEach
    void setUpConcert() {
        concert = Concert.builder()
                .title("테스트 콘서트")
                .category(ConcertCategory.DOMESTIC_ARTIST)
                .build();
        entityManager.persist(concert);
    }

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
                .concert(concert)
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
                CompanionPostSpecifications.concertIdEquals(concert.getId()),
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
                CompanionPostSpecifications.concertIdEquals(concert.getId()),
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
                CompanionPostSpecifications.concertIdEquals(concert.getId()),
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
                .concert(concert)
                .watchDay(WatchDay.DAY1)
                .activities(Set.of(CompanionActivity.CONCERT))
                .build();
        hiddenPost.toggleVisible(false);
        entityManager.persist(hiddenPost);
        entityManager.flush();

        Specification<CompanionPost> spec = Specification.allOf(
                CompanionPostSpecifications.concertIdEquals(concert.getId()),
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
                CompanionPostSpecifications.concertIdEquals(concert.getId()),
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
                CompanionPostSpecifications.concertIdEquals(concert.getId()),
                CompanionPostSpecifications.watchDayEquals(WatchDay.DAY1),
                CompanionPostSpecifications.hasNoBlockRelationWith(me.getId()));

        List<CompanionPost> result = companionPostRepository.findAll(spec);

        assertThat(result).extracting(post -> post.getUser().getProviderId())
                .containsExactly("stranger2");
    }

    @Test
    void 작성자가_탈퇴한_프로필은_목록에서_제외된다() {
        User active = persistUser("wactive", Gender.MALE, 25);
        User withdrawn = persistUser("wwithdrawn", Gender.MALE, 25);
        persistCompanionPost(active);
        persistCompanionPost(withdrawn);
        withdrawn.withdraw();
        entityManager.flush();

        Specification<CompanionPost> spec = Specification.allOf(
                CompanionPostSpecifications.concertIdEquals(concert.getId()),
                CompanionPostSpecifications.watchDayEquals(WatchDay.DAY1),
                CompanionPostSpecifications.authorNotWithdrawn());

        List<CompanionPost> result = companionPostRepository.findAll(spec);

        assertThat(result).extracting(post -> post.getUser().getProviderId())
                .containsExactly("wactive");
    }

    @Test
    void 성별_조건으로_필터링된다() {
        User male = persistUser("male", Gender.MALE, 25);
        User female = persistUser("female", Gender.FEMALE, 25);
        persistCompanionPost(male);
        persistCompanionPost(female);
        entityManager.flush();

        Specification<CompanionPost> spec = Specification.allOf(
                CompanionPostSpecifications.concertIdEquals(concert.getId()),
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
                CompanionPostSpecifications.concertIdEquals(concert.getId()),
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
                CompanionPostSpecifications.concertIdEquals(concert.getId()),
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
                CompanionPostSpecifications.concertIdEquals(concert.getId()),
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
                CompanionPostSpecifications.concertIdEquals(concert.getId()),
                CompanionPostSpecifications.watchDayEquals(WatchDay.DAY1),
                CompanionPostSpecifications.ageGroupIn(List.of(AgeGroup.ANY)));

        List<CompanionPost> result = companionPostRepository.findAll(spec);

        assertThat(result).extracting(post -> post.getUser().getProviderId())
                .containsExactlyInAnyOrder("twenties2", "thirties2");
    }

    @Test
    void 매칭_목록에서는_본인을_제외하고_조회자가_볼_수_있는_프로필만_필터링된다() {
        // persistUser는 providerId 앞 5자를 닉네임으로 쓰므로(유니크 제약), 앞 5자가 서로 겹치지 않게 짓는다
        User me = persistUser("meCnt", Gender.MALE, 25);
        User visible = persistUser("visCnt", Gender.MALE, 25);
        User sameGenderOnlySameGender = persistUser("sgSame", Gender.MALE, 25);
        User sameGenderOnlyOppositeGender = persistUser("sgOpp", Gender.FEMALE, 25);
        User hidden = persistUser("hidCnt", Gender.MALE, 25);
        User withdrawn = persistUser("wdCnt", Gender.MALE, 25);
        User blockedByMe = persistUser("bbmCnt", Gender.MALE, 25);
        User blockedMe = persistUser("bmeCnt", Gender.MALE, 25);

        persistCompanionPost(me);
        persistCompanionPost(visible);
        persistCompanionPost(sameGenderOnlySameGender, Set.of(CompanionActivity.CONCERT), true);
        persistCompanionPost(sameGenderOnlyOppositeGender, Set.of(CompanionActivity.CONCERT), true);

        CompanionPost hiddenPost = CompanionPost.builder()
                .user(hidden)
                .concert(concert)
                .watchDay(WatchDay.DAY1)
                .activities(Set.of(CompanionActivity.CONCERT))
                .build();
        hiddenPost.toggleVisible(false);
        entityManager.persist(hiddenPost);

        persistCompanionPost(withdrawn);
        withdrawn.withdraw();

        persistCompanionPost(blockedByMe);
        entityManager.persist(Block.builder().blocker(me).blocked(blockedByMe).build());

        persistCompanionPost(blockedMe);
        entityManager.persist(Block.builder().blocker(blockedMe).blocked(me).build());

        entityManager.flush();

        Specification<CompanionPost> visibleToMeSpec = Specification.allOf(
                CompanionPostSpecifications.concertIdEquals(concert.getId()),
                CompanionPostSpecifications.userIdNotEquals(me.getId()),
                CompanionPostSpecifications.isVisible(),
                CompanionPostSpecifications.authorNotWithdrawn(),
                CompanionPostSpecifications.hasNoBlockRelationWith(me.getId()),
                CompanionPostSpecifications.respectsSameGenderOnly(me.getGender()));

        long count = companionPostRepository.count(visibleToMeSpec);

        // 나(me)를 제외하고, visible + sameGenderOnlySameGender 만 남는다:
        // hidden(비공개), withdrawn(탈퇴), blockedByMe/blockedMe(차단), sameGenderOnlyOppositeGender(이성 공개제한)는 제외
        assertThat(count).isEqualTo(2);
    }

    @Test
    void 콘서트_동행_수를_셀_때는_공개된_본인_프로필도_포함된다() {
        User me = persistUser("meCnt2", Gender.MALE, 25);
        User visible = persistUser("visCnt2", Gender.MALE, 25);
        User hidden = persistUser("hidCnt2", Gender.MALE, 25);
        User blockedByMe = persistUser("bbmCnt2", Gender.MALE, 25);

        persistCompanionPost(me);
        persistCompanionPost(visible);

        CompanionPost hiddenPost = CompanionPost.builder()
                .user(hidden)
                .concert(concert)
                .watchDay(WatchDay.DAY1)
                .activities(Set.of(CompanionActivity.CONCERT))
                .build();
        hiddenPost.toggleVisible(false);
        entityManager.persist(hiddenPost);

        persistCompanionPost(blockedByMe);
        entityManager.persist(Block.builder().blocker(me).blocked(blockedByMe).build());

        entityManager.flush();

        // countVisibleCompanions()와 동일한 구성 - userIdNotEquals 없이 나머지 조건만
        Specification<CompanionPost> countSpec = Specification.allOf(
                CompanionPostSpecifications.concertIdEquals(concert.getId()),
                CompanionPostSpecifications.isVisible(),
                CompanionPostSpecifications.authorNotWithdrawn(),
                CompanionPostSpecifications.hasNoBlockRelationWith(me.getId()),
                CompanionPostSpecifications.respectsSameGenderOnly(me.getGender()));

        long count = companionPostRepository.count(countSpec);

        // 나(공개) + visible = 2. hidden(비공개), blockedByMe(차단)는 제외
        assertThat(count).isEqualTo(2);
    }

    @Test
    void 콘서트_동행_수를_셀_때_본인_프로필이_비공개면_제외된다() {
        User me = persistUser("meCnt3", Gender.MALE, 25);
        User visible = persistUser("visCnt3", Gender.MALE, 25);

        CompanionPost myPost = CompanionPost.builder()
                .user(me)
                .concert(concert)
                .watchDay(WatchDay.DAY1)
                .activities(Set.of(CompanionActivity.CONCERT))
                .build();
        myPost.toggleVisible(false);
        entityManager.persist(myPost);

        persistCompanionPost(visible);
        entityManager.flush();

        Specification<CompanionPost> countSpec = Specification.allOf(
                CompanionPostSpecifications.concertIdEquals(concert.getId()),
                CompanionPostSpecifications.isVisible(),
                CompanionPostSpecifications.authorNotWithdrawn(),
                CompanionPostSpecifications.hasNoBlockRelationWith(me.getId()),
                CompanionPostSpecifications.respectsSameGenderOnly(me.getGender()));

        long count = companionPostRepository.count(countSpec);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void 관람일이_지난_프로필은_watchDayNotExpired_스펙에서_제외된다() {
        User author = persistUser("exp1", Gender.MALE, 25);
        LocalDate cutoff = LocalDate.now();
        Concert pastConcert = persistConcert(cutoff.minusDays(1));
        persistCompanionPostFor(author, pastConcert, WatchDay.DAY1);
        entityManager.flush();

        Specification<CompanionPost> spec = Specification.allOf(
                CompanionPostSpecifications.concertIdEquals(pastConcert.getId()),
                CompanionPostSpecifications.watchDayNotExpired(cutoff));

        assertThat(companionPostRepository.count(spec)).isZero();
    }

    @Test
    void 관람일이_컷오프_당일이거나_미래인_프로필은_watchDayNotExpired_스펙에_포함된다() {
        User todayAuthor = persistUser("exp2", Gender.MALE, 25);
        User futureAuthor = persistUser("exp3", Gender.MALE, 25);
        LocalDate cutoff = LocalDate.now();
        Concert todayConcert = persistConcert(cutoff);
        Concert futureConcert = persistConcert(cutoff.plusDays(5));
        persistCompanionPostFor(todayAuthor, todayConcert, WatchDay.DAY1);
        persistCompanionPostFor(futureAuthor, futureConcert, WatchDay.DAY1);
        entityManager.flush();

        assertThat(companionPostRepository.count(CompanionPostSpecifications.watchDayNotExpired(cutoff)))
                .isEqualTo(2);
    }

    @Test
    void DAY2_프로필의_관람일은_콘서트_시작일_다음날로_계산된다() {
        User author = persistUser("exp4", Gender.MALE, 25);
        LocalDate cutoff = LocalDate.now();
        // 콘서트가 컷오프 하루 전 시작 -> DAY2 관람일은 컷오프 당일 -> 아직 지나지 않음
        Concert concert = persistConcert(cutoff.minusDays(1));
        persistCompanionPostFor(author, concert, WatchDay.DAY2);
        entityManager.flush();

        Specification<CompanionPost> spec = Specification.allOf(
                CompanionPostSpecifications.concertIdEquals(concert.getId()),
                CompanionPostSpecifications.watchDayNotExpired(cutoff));

        assertThat(companionPostRepository.count(spec)).isEqualTo(1);
    }

    @Test
    void 콘서트_날짜가_미정이면_관람일이_지난_것으로_취급하지_않는다() {
        User author = persistUser("exp5", Gender.MALE, 25);
        Concert undatedConcert = persistConcert(null);
        persistCompanionPostFor(author, undatedConcert, WatchDay.DAY1);
        entityManager.flush();

        Specification<CompanionPost> spec = Specification.allOf(
                CompanionPostSpecifications.concertIdEquals(undatedConcert.getId()),
                CompanionPostSpecifications.watchDayNotExpired(LocalDate.now()));

        assertThat(companionPostRepository.count(spec)).isEqualTo(1);
    }

    @Test
    void 컷오프가_다음날_오전_10시_경계에서_정확히_전환된다() {
        User author = persistUser("exp6", Gender.MALE, 25);
        LocalDate watchDate = LocalDate.now().minusDays(1);
        Concert concert = persistConcert(watchDate);
        persistCompanionPostFor(author, concert, WatchDay.DAY1);
        entityManager.flush();

        Specification<CompanionPost> spec = Specification.allOf(
                CompanionPostSpecifications.concertIdEquals(concert.getId()),
                CompanionPostSpecifications.watchDayNotExpired(
                        ExpiryCutoff.cutoffDate(watchDate.plusDays(1).atTime(9, 59))));
        assertThat(companionPostRepository.count(spec)).isEqualTo(1);

        Specification<CompanionPost> specAfter = Specification.allOf(
                CompanionPostSpecifications.concertIdEquals(concert.getId()),
                CompanionPostSpecifications.watchDayNotExpired(
                        ExpiryCutoff.cutoffDate(watchDate.plusDays(1).atTime(10, 0))));
        assertThat(companionPostRepository.count(specAfter)).isZero();
    }

    private User persistUserWithEmail(String providerId, String email, boolean concertReminderEmail) {
        User user = User.builder()
                .authProvider(AuthProvider.KAKAO)
                .providerId(providerId)
                .status(Status.ACTIVE)
                .onboardingCompleted(true)
                .build();
        user.completeOnboarding(providerId.substring(0, Math.min(5, providerId.length())),
                LocalDate.now().minusYears(25), Gender.MALE);
        // 도메인 필드 직접 세팅 (엔티티에 setter가 없음)
        org.springframework.test.util.ReflectionTestUtils.setField(user, "email", email);
        org.springframework.test.util.ReflectionTestUtils.setField(user, "concertReminderEmail", concertReminderEmail);
        entityManager.persist(user);
        return user;
    }

    private Concert persistConcert(LocalDate startDate) {
        Concert concert = Concert.builder()
                .title("공연")
                .venue("장소")
                .startDate(startDate)
                .endDate(startDate)
                .category(ConcertCategory.J_POP_ARTIST)
                .build();
        entityManager.persist(concert);
        return concert;
    }

    private void persistCompanionPostFor(User user, Concert concert, WatchDay watchDay) {
        entityManager.persist(CompanionPost.builder()
                .user(user)
                .concert(concert)
                .watchDay(watchDay)
                .activities(Set.of(CompanionActivity.CONCERT))
                .sameGenderOnly(false)
                .build());
    }

    @Test
    void 리마인더_쿼리는_DAY1_공연이_내일_시작할_때_대상자를_반환한다() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        User user = persistUserWithEmail("rmd1", "a@a.com", true);
        Concert concert = persistConcert(tomorrow);
        persistCompanionPostFor(user, concert, WatchDay.DAY1);
        entityManager.flush();

        List<ConcertReminderRow> rows = companionPostRepository.findConcertReminderRows(
                tomorrow, tomorrow.minusDays(1), tomorrow.minusDays(2), tomorrow.minusDays(3));

        assertThat(rows).extracting(ConcertReminderRow::recipientEmail)
                .containsExactly("a@a.com");
    }

    @Test
    void 리마인더_쿼리는_DAY2_공연이_오늘_시작하면_대상자를_반환한다() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDate today = LocalDate.now();
        User user = persistUserWithEmail("rmd2", "b@b.com", true);
        Concert concert = persistConcert(today);
        persistCompanionPostFor(user, concert, WatchDay.DAY2);
        entityManager.flush();

        List<ConcertReminderRow> rows = companionPostRepository.findConcertReminderRows(
                tomorrow, tomorrow.minusDays(1), tomorrow.minusDays(2), tomorrow.minusDays(3));

        assertThat(rows).extracting(ConcertReminderRow::recipientEmail)
                .containsExactly("b@b.com");
    }

    @Test
    void 리마인더_쿼리는_concertReminderEmail_false인_유저를_제외한다() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        User enabled = persistUserWithEmail("on", "on@t.com", true);
        User disabled = persistUserWithEmail("off", "off@t.com", false);
        Concert concert = persistConcert(tomorrow);
        persistCompanionPostFor(enabled, concert, WatchDay.DAY1);
        persistCompanionPostFor(disabled, concert, WatchDay.DAY1);
        entityManager.flush();

        List<ConcertReminderRow> rows = companionPostRepository.findConcertReminderRows(
                tomorrow, tomorrow.minusDays(1), tomorrow.minusDays(2), tomorrow.minusDays(3));

        assertThat(rows).extracting(ConcertReminderRow::recipientEmail)
                .containsExactly("on@t.com");
    }

    @Test
    void 리마인더_쿼리는_이메일이_없는_유저를_제외한다() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        User noEmail = persistUserWithEmail("noem", null, true);
        Concert concert = persistConcert(tomorrow);
        persistCompanionPostFor(noEmail, concert, WatchDay.DAY1);
        entityManager.flush();

        List<ConcertReminderRow> rows = companionPostRepository.findConcertReminderRows(
                tomorrow, tomorrow.minusDays(1), tomorrow.minusDays(2), tomorrow.minusDays(3));

        assertThat(rows).isEmpty();
    }

    @Test
    void 리마인더_쿼리는_다른_날짜에_시작하는_공연을_제외한다() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        User user = persistUserWithEmail("far", "far@t.com", true);
        Concert farConcert = persistConcert(tomorrow.plusDays(5));
        persistCompanionPostFor(user, farConcert, WatchDay.DAY1);
        entityManager.flush();

        List<ConcertReminderRow> rows = companionPostRepository.findConcertReminderRows(
                tomorrow, tomorrow.minusDays(1), tomorrow.minusDays(2), tomorrow.minusDays(3));

        assertThat(rows).isEmpty();
    }
}