package com.loop.loop_backend.ConcertScrap.repository;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.ConcertScrap.domain.ConcertScrap;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Gender;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 스크랩 목록 요구사항:
// - 예정/지난 구분은 공연 목록과 같은 규칙: 종료일(없으면 시작일)이 기준일 이상이면 예정, 미만이면 지난 공연.
//   날짜 미정 공연은 지난 공연이 아니므로 예정 쪽에 들어간다.
// - 정렬: 예정은 가까운 날짜순(날짜 미정은 맨 뒤), 지난 공연은 최근 종료순.
// - 내가 스크랩한 것만 보인다.
// 기준일은 쿼리 파라미터로 받으므로 고정 날짜로 검증한다.
@DataJpaTest
class ConcertScrapRepositoryTest {

    private static final LocalDate CUTOFF = LocalDate.of(2026, 9, 29);

    @Autowired
    private ConcertScrapRepository concertScrapRepository;

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

    private Concert persistConcert(String title, LocalDate startDate, LocalDate endDate) {
        Concert concert = Concert.builder()
                .title(title)
                .category(ConcertCategory.J_POP_ARTIST)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        entityManager.persist(concert);
        return concert;
    }

    private void scrap(User user, Concert concert) {
        entityManager.persist(ConcertScrap.builder().user(user).concert(concert).build());
    }

    private static List<String> titles(List<Concert> concerts) {
        return concerts.stream().map(Concert::getTitle).toList();
    }

    // ---------- 예정 / 지난 구분 ----------

    @Test
    void 예정_목록에는_종료일이_기준일_이후인_공연과_날짜_미정_공연만_들어간다() {
        User me = persistUser("me");
        scrap(me, persistConcert("예정", CUTOFF.plusDays(5), CUTOFF.plusDays(6)));
        scrap(me, persistConcert("지난", CUTOFF.minusDays(5), CUTOFF.minusDays(4)));
        scrap(me, persistConcert("미정", null, null));

        List<Concert> upcoming = concertScrapRepository.findUpcomingOrUndatedScrappedConcerts(me.getId(), CUTOFF);

        assertThat(titles(upcoming)).containsExactlyInAnyOrder("예정", "미정");
    }

    @Test
    void 지난_목록에는_종료일이_기준일_이전인_공연만_들어가고_날짜_미정은_제외된다() {
        User me = persistUser("me");
        scrap(me, persistConcert("예정", CUTOFF.plusDays(5), CUTOFF.plusDays(6)));
        scrap(me, persistConcert("지난", CUTOFF.minusDays(5), CUTOFF.minusDays(4)));
        scrap(me, persistConcert("미정", null, null));

        List<Concert> past = concertScrapRepository.findPastScrappedConcerts(me.getId(), CUTOFF);

        assertThat(titles(past)).containsExactly("지난");
    }

    @Test
    void 이미_시작했지만_종료일이_남은_공연은_예정이다() {
        User me = persistUser("me");
        scrap(me, persistConcert("진행중", CUTOFF.minusDays(1), CUTOFF.plusDays(1)));

        assertThat(titles(concertScrapRepository.findUpcomingOrUndatedScrappedConcerts(me.getId(), CUTOFF)))
                .containsExactly("진행중");
        assertThat(concertScrapRepository.findPastScrappedConcerts(me.getId(), CUTOFF)).isEmpty();
    }

    @Test
    void 기준일에_끝나는_공연은_아직_예정이다() {
        User me = persistUser("me");
        scrap(me, persistConcert("기준일 종료", CUTOFF, CUTOFF));

        assertThat(titles(concertScrapRepository.findUpcomingOrUndatedScrappedConcerts(me.getId(), CUTOFF)))
                .containsExactly("기준일 종료");
        assertThat(concertScrapRepository.findPastScrappedConcerts(me.getId(), CUTOFF)).isEmpty();
    }

    @Test
    void 종료일이_없으면_시작일로_예정_지난을_판단한다() {
        User me = persistUser("me");
        scrap(me, persistConcert("하루 예정", CUTOFF.plusDays(3), null));
        scrap(me, persistConcert("하루 지난", CUTOFF.minusDays(3), null));

        assertThat(titles(concertScrapRepository.findUpcomingOrUndatedScrappedConcerts(me.getId(), CUTOFF)))
                .containsExactly("하루 예정");
        assertThat(titles(concertScrapRepository.findPastScrappedConcerts(me.getId(), CUTOFF)))
                .containsExactly("하루 지난");
    }

    // ---------- 정렬 ----------

    @Test
    void 예정_목록은_가까운_날짜순이고_날짜_미정은_맨_뒤다() {
        User me = persistUser("me");
        scrap(me, persistConcert("미정", null, null));
        scrap(me, persistConcert("30일 뒤", CUTOFF.plusDays(30), CUTOFF.plusDays(30)));
        scrap(me, persistConcert("3일 뒤", CUTOFF.plusDays(3), CUTOFF.plusDays(3)));
        scrap(me, persistConcert("10일 뒤", CUTOFF.plusDays(10), CUTOFF.plusDays(10)));

        List<Concert> upcoming = concertScrapRepository.findUpcomingOrUndatedScrappedConcerts(me.getId(), CUTOFF);

        assertThat(titles(upcoming)).containsExactly("3일 뒤", "10일 뒤", "30일 뒤", "미정");
    }

    @Test
    void 지난_목록은_최근에_끝난_공연부터_보여준다() {
        User me = persistUser("me");
        scrap(me, persistConcert("30일 전", CUTOFF.minusDays(30), CUTOFF.minusDays(30)));
        scrap(me, persistConcert("3일 전", CUTOFF.minusDays(3), CUTOFF.minusDays(3)));
        scrap(me, persistConcert("10일 전", CUTOFF.minusDays(10), CUTOFF.minusDays(10)));

        List<Concert> past = concertScrapRepository.findPastScrappedConcerts(me.getId(), CUTOFF);

        assertThat(titles(past)).containsExactly("3일 전", "10일 전", "30일 전");
    }

    // ---------- 유저 격리 ----------

    @Test
    void 다른_유저가_스크랩한_공연은_내_목록에_나오지_않는다() {
        User me = persistUser("me");
        User other = persistUser("other");
        scrap(me, persistConcert("내 예정", CUTOFF.plusDays(5), CUTOFF.plusDays(5)));
        scrap(other, persistConcert("남의 예정", CUTOFF.plusDays(5), CUTOFF.plusDays(5)));
        scrap(me, persistConcert("내 지난", CUTOFF.minusDays(5), CUTOFF.minusDays(5)));
        scrap(other, persistConcert("남의 지난", CUTOFF.minusDays(5), CUTOFF.minusDays(5)));

        assertThat(titles(concertScrapRepository.findUpcomingOrUndatedScrappedConcerts(me.getId(), CUTOFF)))
                .containsExactly("내 예정");
        assertThat(titles(concertScrapRepository.findPastScrappedConcerts(me.getId(), CUTOFF)))
                .containsExactly("내 지난");
    }

    @Test
    void 스크랩하지_않은_공연은_목록에_나오지_않는다() {
        User me = persistUser("me");
        persistConcert("스크랩 안 함", CUTOFF.plusDays(5), CUTOFF.plusDays(5));

        assertThat(concertScrapRepository.findUpcomingOrUndatedScrappedConcerts(me.getId(), CUTOFF)).isEmpty();
    }

    // ---------- 무결성 ----------

    @Test
    void 같은_유저가_같은_공연을_두_번_스크랩하는_것은_DB가_막는다() {
        User me = persistUser("me");
        Concert concert = persistConcert("공연", CUTOFF.plusDays(5), CUTOFF.plusDays(5));
        concertScrapRepository.saveAndFlush(ConcertScrap.builder().user(me).concert(concert).build());

        assertThatThrownBy(() -> concertScrapRepository.saveAndFlush(
                ConcertScrap.builder().user(me).concert(concert).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 공연이_삭제되면_그_공연의_스크랩도_함께_삭제된다() {
        User me = persistUser("me");
        Concert concert = persistConcert("삭제될 공연", CUTOFF.plusDays(5), CUTOFF.plusDays(5));
        scrap(me, concert);
        entityManager.flush();
        entityManager.clear();

        entityManager.remove(entityManager.find(Concert.class, concert.getId()));
        entityManager.flush();
        entityManager.clear();

        assertThat(concertScrapRepository.existsByUser_IdAndConcert_Id(me.getId(), concert.getId())).isFalse();
    }

    @Test
    void 유저의_스크랩_전체_삭제는_다른_유저의_스크랩을_건드리지_않는다() {
        User me = persistUser("me");
        User other = persistUser("other");
        Concert concert = persistConcert("공연", CUTOFF.plusDays(5), CUTOFF.plusDays(5));
        scrap(me, concert);
        scrap(other, concert);
        entityManager.flush();

        concertScrapRepository.deleteAllByUser(me);
        entityManager.flush();
        entityManager.clear();

        assertThat(concertScrapRepository.existsByUser_IdAndConcert_Id(me.getId(), concert.getId())).isFalse();
        assertThat(concertScrapRepository.existsByUser_IdAndConcert_Id(other.getId(), concert.getId())).isTrue();
    }
}