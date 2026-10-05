package com.loop.loop_backend.Concert.repository;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.dto.ConcertSummaryDto;
import com.loop.loop_backend.ConcertScrap.domain.ConcertScrap;
import com.loop.loop_backend.ConcertScrap.repository.ConcertScrapRepository;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Gender;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.Venue.domain.Venue;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

// 공연 목록 요구사항(공연 탭 목록·검색·아티스트별·스크랩 목록 공통):
// - 목록의 공연장은 공연장 관리의 공연장 정보다.
// - 목록 한 번에 공연 수와 상관없이 쿼리 수가 같다 - 공연마다 아티스트·공연장을 따로 조회하지 않는다(N+1 없음).
// 목록 응답(ConcertSummaryDto)으로 바꾸는 것까지 쿼리 수에 포함해서 본다. 기준일은 고정 날짜로 둔다.
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ConcertListQueryCountTest {

    private static final LocalDate CUTOFF = LocalDate.of(2026, 10, 5);
    private static final LocalDate UPCOMING = CUTOFF.plusDays(30);
    private static final LocalDate PAST = CUTOFF.minusDays(30);
    private static final List<ConcertCategory> J_POP = List.of(ConcertCategory.J_POP_ARTIST);

    @Autowired private ConcertRepository concertRepository;
    @Autowired private ConcertScrapRepository concertScrapRepository;
    @Autowired private EntityManager em;

    private User me;
    private Artist sharedArtist;
    private int seq;

    @BeforeEach
    void setUp() {
        me = User.builder().authProvider(AuthProvider.KAKAO).providerId("me").status(Status.ACTIVE)
                .onboardingCompleted(true).build();
        me.completeOnboarding("me", LocalDate.now().minusYears(25), Gender.MALE);
        em.persist(me);
        sharedArtist = artist("Vaundy");
    }

    private Artist artist(String name) {
        Artist artist = Artist.builder().name(name).category(ConcertCategory.J_POP_ARTIST).build();
        em.persist(artist);
        return artist;
    }

    // 공연마다 다른 아티스트(공유 아티스트 지정 시 그 아티스트)·다른 공연장. 내가 스크랩한 공연이다.
    private void addConcerts(int count, LocalDate date, boolean shareArtist) {
        for (int i = 0; i < count; i++) {
            int n = ++seq;
            Venue venue = Venue.builder().name("공연장 " + n).address("서울특별시 송파구").build();
            em.persist(venue);
            Concert concert = Concert.builder().title("공연 " + n).category(ConcertCategory.J_POP_ARTIST)
                    .artist(shareArtist ? sharedArtist : artist("아티스트 " + n))
                    .startDate(date).endDate(date).build();
            concert.changeVenue(venue);
            em.persist(concert);
            em.persist(ConcertScrap.builder().user(me).concert(concert).build());
        }
    }

    private long queryCount(Supplier<List<Concert>> listQuery) {
        em.flush();
        em.clear();
        Statistics stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        listQuery.get().stream().map(ConcertSummaryDto::from).toList();
        return stats.getPrepareStatementCount();
    }

    private void assertSameQueryCount(LocalDate date, boolean shareArtist, Supplier<List<Concert>> listQuery) {
        addConcerts(1, date, shareArtist);
        long withOne = queryCount(listQuery);

        addConcerts(4, date, shareArtist);
        long withFive = queryCount(listQuery);

        assertThat(withOne).isPositive(); // 통계가 꺼져 있으면 둘 다 0이라 비교가 의미 없다
        assertThat(withFive).isEqualTo(withOne);
    }

    // ---------- 공연장 ----------

    @Test
    void 목록의_공연장은_공연장_관리의_공연장_정보다() {
        addConcerts(1, UPCOMING, false);
        em.flush();
        em.clear();

        ConcertSummaryDto row = ConcertSummaryDto.from(
                concertRepository.findUpcomingOrUndatedByCategories(J_POP, CUTOFF).get(0));

        assertThat(row.getVenue().name()).isEqualTo("공연장 1");
        assertThat(row.getVenue().address()).isEqualTo("서울특별시 송파구");
    }

    // ---------- 쿼리 수 ----------

    @Test
    void 공연_탭_예정_목록() {
        assertSameQueryCount(UPCOMING, false, () -> concertRepository.findUpcomingOrUndatedByCategories(J_POP, CUTOFF));
    }

    @Test
    void 공연_탭_지난_목록() {
        assertSameQueryCount(PAST, false, () -> concertRepository.findPastByCategories(J_POP, CUTOFF));
    }

    @Test
    void 예정_공연_검색() {
        assertSameQueryCount(UPCOMING, false,
                () -> concertRepository.searchUpcomingOrUndatedByTitleAndCategories("공연", J_POP, CUTOFF));
    }

    @Test
    void 지난_공연_검색() {
        assertSameQueryCount(PAST, false,
                () -> concertRepository.searchPastByTitleAndCategories("공연", J_POP, CUTOFF));
    }

    @Test
    void 아티스트별_예정_목록() {
        assertSameQueryCount(UPCOMING, true,
                () -> concertRepository.findUpcomingOrUndatedByArtistId(sharedArtist.getId(), CUTOFF));
    }

    @Test
    void 스크랩_예정_목록() {
        assertSameQueryCount(UPCOMING, false,
                () -> concertScrapRepository.findUpcomingOrUndatedScrappedConcerts(me.getId(), CUTOFF));
    }

    @Test
    void 스크랩_지난_목록() {
        assertSameQueryCount(PAST, false,
                () -> concertScrapRepository.findPastScrappedConcerts(me.getId(), CUTOFF));
    }
}