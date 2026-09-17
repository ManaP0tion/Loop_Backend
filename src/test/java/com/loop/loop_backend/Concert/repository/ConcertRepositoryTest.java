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
        return persistConcert(title, startDate, endDate, ConcertCategory.DOMESTIC_ARTIST);
    }

    private Concert persistConcert(String title, LocalDate startDate, LocalDate endDate, ConcertCategory category) {
        Concert concert = Concert.builder()
                .title(title)
                .category(category)
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

    // ===== findUpcomingOrUndatedByCategories (section 예정 공연 조회) =====
    // 규칙: 요청 카테고리에 속하고, 종료일(없으면 시작일)이 cutoff 이후면 포함. 날짜 미정은 항상 포함.
    // 정렬은 가까운 날짜순, 날짜 미정은 맨 뒤.

    @Test
    void 예정_조회시_시작일은_지났지만_종료일이_남은_연장_공연은_포함된다() {
        LocalDate today = LocalDate.now();
        persistConcert("연장 공연", today.minusDays(2), today.plusDays(1));
        entityManager.flush();

        List<Concert> result = concertRepository.findUpcomingOrUndatedByCategories(
                List.of(ConcertCategory.DOMESTIC_ARTIST), today);

        assertThat(result).extracting(Concert::getTitle).containsExactly("연장 공연");
    }

    @Test
    void 예정_조회시_이미_종료일이_지난_공연은_제외된다() {
        LocalDate today = LocalDate.now();
        persistConcert("종료된 공연", today.minusDays(5), today.minusDays(1));
        entityManager.flush();

        List<Concert> result = concertRepository.findUpcomingOrUndatedByCategories(
                List.of(ConcertCategory.DOMESTIC_ARTIST), today);

        assertThat(result).isEmpty();
    }

    @Test
    void 예정_조회시_종료일이_없으면_시작일을_기준으로_판단한다() {
        LocalDate today = LocalDate.now();
        persistConcert("종료일_없음_미래", today.plusDays(1), null);
        persistConcert("종료일_없음_과거", today.minusDays(1), null);
        entityManager.flush();

        List<Concert> result = concertRepository.findUpcomingOrUndatedByCategories(
                List.of(ConcertCategory.DOMESTIC_ARTIST), today);

        assertThat(result).extracting(Concert::getTitle).containsExactly("종료일_없음_미래");
    }

    @Test
    void 예정_조회시_시작일_종료일_모두_없으면_날짜_미정으로_항상_포함된다() {
        LocalDate today = LocalDate.now();
        persistConcert("날짜_미정", null, null);
        entityManager.flush();

        List<Concert> result = concertRepository.findUpcomingOrUndatedByCategories(
                List.of(ConcertCategory.DOMESTIC_ARTIST), today);

        assertThat(result).extracting(Concert::getTitle).containsExactly("날짜_미정");
    }

    @Test
    void 예정_조회시_요청한_카테고리에_속하지_않는_공연은_제외된다() {
        LocalDate today = LocalDate.now();
        persistConcert("J팝 내한", today.plusDays(1), today.plusDays(1), ConcertCategory.J_POP_ARTIST);
        persistConcert("국내 페스티벌", today.plusDays(1), today.plusDays(1), ConcertCategory.DOMESTIC_FESTIVAL);
        entityManager.flush();

        List<Concert> result = concertRepository.findUpcomingOrUndatedByCategories(
                List.of(ConcertCategory.J_POP_ARTIST), today);

        assertThat(result).extracting(Concert::getTitle).containsExactly("J팝 내한");
    }

    @Test
    void 예정_조회시_카테고리_여러개를_한번에_조회할_수_있다() {
        LocalDate today = LocalDate.now();
        persistConcert("J팝 내한", today.plusDays(1), today.plusDays(1), ConcertCategory.J_POP_ARTIST);
        persistConcert("일본 페스티벌", today.plusDays(2), today.plusDays(2), ConcertCategory.JAPAN_FESTIVAL);
        persistConcert("국내 아티스트", today.plusDays(1), today.plusDays(1), ConcertCategory.DOMESTIC_ARTIST);
        entityManager.flush();

        List<Concert> result = concertRepository.findUpcomingOrUndatedByCategories(
                List.of(ConcertCategory.J_POP_ARTIST, ConcertCategory.JAPAN_FESTIVAL), today);

        assertThat(result).extracting(Concert::getTitle)
                .containsExactlyInAnyOrder("J팝 내한", "일본 페스티벌");
    }

    @Test
    void 예정_조회_결과는_가까운_시작일_순이고_날짜_미정은_맨_뒤로_간다() {
        LocalDate today = LocalDate.now();
        persistConcert("날짜_미정", null, null);
        persistConcert("먼_공연", today.plusDays(10), today.plusDays(10));
        persistConcert("가까운_공연", today.plusDays(1), today.plusDays(1));
        entityManager.flush();

        List<Concert> result = concertRepository.findUpcomingOrUndatedByCategories(
                List.of(ConcertCategory.DOMESTIC_ARTIST), today);

        assertThat(result).extracting(Concert::getTitle)
                .containsExactly("가까운_공연", "먼_공연", "날짜_미정");
    }

    // ===== findPastByCategories (section 지난 공연 조회) =====
    // 규칙: 요청 카테고리에 속하고, 종료일(없으면 시작일)이 cutoff 이전이면 포함. 날짜 미정은 절대 포함되지 않는다.
    // 정렬은 최근 종료순(내림차순).

    @Test
    void 지난_조회시_이미_종료된_공연만_포함된다() {
        LocalDate today = LocalDate.now();
        persistConcert("종료된 공연", today.minusDays(5), today.minusDays(1));
        entityManager.flush();

        List<Concert> result = concertRepository.findPastByCategories(
                List.of(ConcertCategory.DOMESTIC_ARTIST), today);

        assertThat(result).extracting(Concert::getTitle).containsExactly("종료된 공연");
    }

    @Test
    void 지난_조회시_아직_끝나지_않은_공연은_제외된다() {
        LocalDate today = LocalDate.now();
        persistConcert("연장 공연", today.minusDays(2), today.plusDays(1));
        entityManager.flush();

        List<Concert> result = concertRepository.findPastByCategories(
                List.of(ConcertCategory.DOMESTIC_ARTIST), today);

        assertThat(result).isEmpty();
    }

    @Test
    void 지난_조회시_종료일이_없으면_시작일을_기준으로_판단한다() {
        LocalDate today = LocalDate.now();
        persistConcert("종료일_없음_과거", today.minusDays(1), null);
        persistConcert("종료일_없음_미래", today.plusDays(1), null);
        entityManager.flush();

        List<Concert> result = concertRepository.findPastByCategories(
                List.of(ConcertCategory.DOMESTIC_ARTIST), today);

        assertThat(result).extracting(Concert::getTitle).containsExactly("종료일_없음_과거");
    }

    @Test
    void 지난_조회시_날짜_미정_공연은_절대_포함되지_않는다() {
        LocalDate today = LocalDate.now();
        persistConcert("날짜_미정", null, null);
        entityManager.flush();

        List<Concert> result = concertRepository.findPastByCategories(
                List.of(ConcertCategory.DOMESTIC_ARTIST), today);

        assertThat(result).isEmpty();
    }

    @Test
    void 지난_조회시_요청한_카테고리에_속하지_않는_공연은_제외된다() {
        LocalDate today = LocalDate.now();
        persistConcert("J팝 내한", today.minusDays(1), today.minusDays(1), ConcertCategory.J_POP_ARTIST);
        persistConcert("국내 페스티벌", today.minusDays(1), today.minusDays(1), ConcertCategory.DOMESTIC_FESTIVAL);
        entityManager.flush();

        List<Concert> result = concertRepository.findPastByCategories(
                List.of(ConcertCategory.J_POP_ARTIST), today);

        assertThat(result).extracting(Concert::getTitle).containsExactly("J팝 내한");
    }

    @Test
    void 지난_조회_결과는_최근_종료일_순으로_정렬된다() {
        LocalDate today = LocalDate.now();
        persistConcert("오래전_종료", today.minusDays(10), today.minusDays(10));
        persistConcert("최근_종료", today.minusDays(1), today.minusDays(1));
        entityManager.flush();

        List<Concert> result = concertRepository.findPastByCategories(
                List.of(ConcertCategory.DOMESTIC_ARTIST), today);

        assertThat(result).extracting(Concert::getTitle)
                .containsExactly("최근_종료", "오래전_종료");
    }
}