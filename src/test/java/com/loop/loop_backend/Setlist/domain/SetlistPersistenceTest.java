package com.loop.loop_backend.Setlist.domain;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Performance.domain.Performance;
import com.loop.loop_backend.Performance.repository.PerformanceRepository;
import com.loop.loop_backend.Setlist.repository.SetlistRepository;
import com.loop.loop_backend.Setlist.repository.SetlistResultRecipient;
import com.loop.loop_backend.Setlist.repository.SetlistVoteRepository;
import com.loop.loop_backend.Setlist.repository.SongVoteCount;
import com.loop.loop_backend.Song.domain.Song;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 셋리스트(AD-07)·투표(NO.65·66) 저장 규칙 - 출연 정보(Performance) 단위:
// - 단독 공연 출연 정보와 페스티벌 헤드라이너에만, (출연 정보, 구분)당 1건. 지난 셋리스트는 날짜·장소 필수.
// - 곡은 순서대로, 같은 곡 중복(앵코르)도 저장된다. 교체는 통째로.
// - 투표는 출연 정보당 유저 1건, 득표 집계는 득표순 → 곡 정렬 순번 순.
// - 출연 정보나 공연이 지워지면 셋리스트·투표도 지워진다.
@DataJpaTest
class SetlistPersistenceTest {

    @Autowired private SetlistRepository setlistRepository;
    @Autowired private SetlistVoteRepository voteRepository;
    @Autowired private ConcertRepository concertRepository;
    @Autowired private PerformanceRepository performanceRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private EntityManager em;

    private Artist yuuri;
    private Concert concert;
    private Performance performance;
    private Song s1, s2, s3;   // 정렬 순번 1, 2, 3

    @BeforeEach
    void setUp() {
        yuuri = artistRepository.save(Artist.builder().name("Yuuri").build());
        concert = concertRepository.save(Concert.builder()
                .title("YUURI LIVE").category(ConcertCategory.STAGE).artist(yuuri)
                .startDate(LocalDate.of(2026, 11, 20)).endDate(LocalDate.of(2026, 11, 20))
                .build());
        performance = performanceRepository.save(Performance.solo(concert, yuuri));
        s1 = song("ドライフラワー", 1);
        s2 = song("ベテルギウス", 2);
        s3 = song("いかないで", 3);
    }

    private Song song(String title, int sortOrder) {
        Song song = Song.builder().artist(yuuri).titleOriginal(title).sortOrder(sortOrder).build();
        em.persist(song);
        return song;
    }

    private User user(String providerId) {
        User user = User.builder().authProvider(AuthProvider.KAKAO).providerId(providerId)
                .status(Status.ACTIVE).onboardingCompleted(true).build();
        em.persist(user);
        return user;
    }

    private Performance festivalLineup(boolean headliner) {
        Concert festival = concertRepository.save(Concert.builder()
                .title("SUMMER SONIC").category(ConcertCategory.FESTIVAL)
                .startDate(LocalDate.of(2026, 11, 20)).endDate(LocalDate.of(2026, 11, 21))
                .build());
        Performance lineup = Performance.builder().concert(festival).artist(yuuri).day(1).displayOrder(1).build();
        lineup.changeHeadliner(headliner);
        return performanceRepository.save(lineup);
    }

    private Setlist actual(List<Song> songs) {
        return setlistRepository.save(Setlist.builder().performance(performance).type(SetlistType.ACTUAL).songs(songs).build());
    }

    private SetlistVote vote(User user, Song... songs) {
        return voteRepository.save(SetlistVote.builder().performance(performance).user(user).songs(Set.of(songs)).build());
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("페스티벌 비헤드라이너에는 셋리스트를 등록할 수 없다")
    void build_WhenFestivalNonHeadliner_ThrowsInvalidInput() {
        // given
        Performance nonHeadliner = festivalLineup(false);

        // when & then
        assertThatThrownBy(() -> Setlist.builder().performance(nonHeadliner).type(SetlistType.ACTUAL).songs(List.of(s1)).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("페스티벌 헤드라이너에는 셋리스트를 등록할 수 있다")
    void build_WhenFestivalHeadliner_Succeeds() {
        // given
        Performance headliner = festivalLineup(true);

        // when
        Setlist setlist = setlistRepository.save(Setlist.builder().performance(headliner).type(SetlistType.ACTUAL)
                .songs(List.of(s1)).build());

        // then
        assertThat(setlist.getId()).isNotNull();
    }

    @Test
    @DisplayName("지난 셋리스트는 날짜·장소가 필수이고 실제 셋리스트는 헤더를 비운다")
    void build_ValidatesPastHeaderAndClearsActualHeader() {
        // when & then
        assertThatThrownBy(() -> Setlist.builder().performance(performance).type(SetlistType.RECENT)
                .performedOn(LocalDate.of(2026, 5, 1)).songs(List.of(s1)).build())
                .isInstanceOf(IllegalArgumentException.class);

        Setlist actual = Setlist.builder().performance(performance).type(SetlistType.ACTUAL)
                .tourName("무시됨").performedOn(LocalDate.of(2026, 5, 1)).venueName("무시됨").songs(List.of(s1)).build();
        assertThat(actual.getTourName()).isNull();
        assertThat(actual.getVenueName()).isNull();
    }

    @Test
    @DisplayName("곡이 없으면 셋리스트를 저장할 수 없다")
    void build_WithNoSongs_ThrowsInvalidInput() {
        // when & then
        assertThatThrownBy(() -> actual(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("같은 출연 정보의 같은 구분 셋리스트는 1건이다")
    void save_WhenSamePerformanceAndType_ViolatesUniqueConstraint() {
        // given
        actual(List.of(s1));

        // when & then
        assertThatThrownBy(() -> {
            actual(List.of(s2));
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("곡은 순서대로 저장되고 중복도 남으며 교체는 통째로 한다")
    void replaceSongs_KeepsOrderAndDuplicatesAndReplacesWhole() {
        // given
        Setlist setlist = actual(List.of(s2, s1, s2));
        flushAndClear();

        // when
        Setlist loaded = setlistRepository.findByPerformanceIdAndType(performance.getId(), SetlistType.ACTUAL).orElseThrow();
        assertThat(loaded.getSongs()).extracting(Song::getId).containsExactly(s2.getId(), s1.getId(), s2.getId());
        loaded.replaceSongs(List.of(em.find(Song.class, s3.getId())));
        flushAndClear();

        // then
        assertThat(setlistRepository.findById(setlist.getId()).orElseThrow().getSongs())
                .extracting(Song::getId).containsExactly(s3.getId());
    }

    @Test
    @DisplayName("결과 메일은 실제 셋리스트에서 한 번만 보낼 차례가 된다")
    void markResultMailSent_OnlyOnceForActual() {
        // given
        Setlist actual = actual(List.of(s1));
        Setlist recent = setlistRepository.save(Setlist.builder().performance(performance).type(SetlistType.RECENT)
                .performedOn(LocalDate.of(2026, 5, 1)).venueName("Zepp").songs(List.of(s1)).build());
        LocalDateTime now = LocalDateTime.of(2026, 11, 21, 12, 0);

        // when & then
        assertThat(actual.markResultMailSent(now)).isTrue();
        assertThat(actual.markResultMailSent(now)).isFalse();
        assertThat(recent.markResultMailSent(now)).isFalse();
    }

    @Test
    @DisplayName("투표는 출연 정보당 유저 1건이다")
    void saveVote_WhenSameUserVotesTwice_ViolatesUniqueConstraint() {
        // given
        User me = user("me");
        vote(me, s1);

        // when & then
        assertThatThrownBy(() -> {
            vote(me, s2);
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("득표 집계는 득표순이고 동점이면 곡 정렬 순번 순이다")
    void countVotesBySong_OrdersByVotesThenSortOrder() {
        // given
        vote(user("a"), s3, s2);
        vote(user("b"), s3, s1);
        vote(user("c"), s2);
        flushAndClear();

        // when & then - s3: 2표, s2: 2표, s1: 1표 → s3·s2 동점은 정렬 순번(2 < 3)으로 s2가 앞
        assertThat(voteRepository.countVotesBySong(performance.getId())).containsExactly(
                new SongVoteCount(s2.getId(), 2), new SongVoteCount(s3.getId(), 2), new SongVoteCount(s1.getId(), 1));
        assertThat(voteRepository.countByPerformanceId(performance.getId())).isEqualTo(3);
    }

    @Test
    @DisplayName("소프트 삭제된 곡은 득표 집계에서 빠진다")
    void countVotesBySong_ExcludesSoftDeletedSongs() {
        // given
        vote(user("a"), s1, s2);
        flushAndClear();
        em.remove(em.find(Song.class, s1.getId()));
        flushAndClear();

        // when & then
        assertThat(voteRepository.countVotesBySong(performance.getId()))
                .extracting(SongVoteCount::songId).containsExactly(s2.getId());
    }

    @Test
    @DisplayName("투표 수정은 곡 선택을 통째로 교체한다")
    void replaceSongs_OnVote_ReplacesWholeSelection() {
        // given
        User me = user("me");
        vote(me, s1, s2);
        flushAndClear();

        // when
        SetlistVote mine = voteRepository.findByPerformanceIdAndUserId(performance.getId(), me.getId()).orElseThrow();
        mine.replaceSongs(Set.of(em.find(Song.class, s3.getId())));
        flushAndClear();

        // then
        assertThat(voteRepository.findByPerformanceIdAndUserId(performance.getId(), me.getId()).orElseThrow().getSongs())
                .extracting(Song::getId).containsExactly(s3.getId());
    }

    @Test
    @DisplayName("탈퇴 정리는 그 유저의 투표만 지운다")
    void deleteAllByUser_RemovesOnlyThatUsersVotes() {
        // given
        User me = user("me");
        vote(me, s1);
        vote(user("other"), s2);
        flushAndClear();

        // when
        voteRepository.deleteAllByUser(em.find(User.class, me.getId()));
        flushAndClear();

        // then
        assertThat(voteRepository.countByPerformanceId(performance.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("결과 메일 대상은 투표·수신 동의·이메일 인증·알림 ON을 모두 만족한 유저다")
    void findResultMailRecipients_ReturnsOnlyFullyEligibleUsers() {
        // given
        User ok = user("ok");
        ok.verifyEmail("ok@loop.com");
        User noConsent = user("noConsent");
        noConsent.verifyEmail("no-consent@loop.com");
        User noEmail = user("noEmail");
        User settingOff = user("settingOff");
        settingOff.verifyEmail("off@loop.com");
        settingOff.updateNotificationSettings(null, null, false);
        User notVoted = user("notVoted");
        notVoted.verifyEmail("not-voted@loop.com");

        vote(ok, s1).agreeResultMail();
        vote(noConsent, s1);
        vote(noEmail, s1).agreeResultMail();
        vote(settingOff, s1).agreeResultMail();
        flushAndClear();

        // when & then
        assertThat(voteRepository.findResultMailRecipients(performance.getId()))
                .extracting(SetlistResultRecipient::email).containsExactly("ok@loop.com");
    }

    @Test
    @DisplayName("셋리스트·투표 존재 여부는 출연 정보 하나 또는 여러 개 단위로 조회된다")
    void existsByPerformance_WhenSetlistOrVoteExists_ReturnsTrue() {
        // given
        Performance empty = festivalLineup(true);
        Performance withVote = festivalLineup(true);
        voteRepository.save(SetlistVote.builder().performance(withVote).user(user("a")).songs(Set.of(s1)).build());
        actual(List.of(s1));
        flushAndClear();

        // when & then
        assertThat(setlistRepository.existsByPerformanceId(performance.getId())).isTrue();
        assertThat(voteRepository.existsByPerformanceId(withVote.getId())).isTrue();
        assertThat(setlistRepository.existsByPerformanceId(empty.getId())).isFalse();
        assertThat(voteRepository.existsByPerformanceId(empty.getId())).isFalse();
        assertThat(setlistRepository.existsByPerformanceIdIn(List.of(empty.getId(), performance.getId()))).isTrue();
        assertThat(voteRepository.existsByPerformanceIdIn(List.of(empty.getId(), withVote.getId()))).isTrue();
        assertThat(setlistRepository.existsByPerformanceIdIn(List.of(empty.getId()))).isFalse();
    }

    @Test
    @DisplayName("공연이 지워지면 출연 정보를 거쳐 셋리스트와 투표도 지워진다")
    void deleteConcert_CascadesToPerformanceSetlistsAndVotes() {
        // given
        actual(List.of(s1, s2));
        vote(user("a"), s1);
        flushAndClear();

        // when
        em.createNativeQuery("DELETE FROM concerts WHERE id = :id").setParameter("id", concert.getId()).executeUpdate();

        // then
        assertThat(performanceRepository.count()).isZero();
        assertThat(setlistRepository.count()).isZero();
        assertThat(voteRepository.count()).isZero();
        // 조인 테이블은 엔티티가 없어 IntelliJ가 테이블을 못 찾는다(실행에는 문제없음)
        //noinspection SqlResolve
        assertThat(em.createNativeQuery("SELECT COUNT(*) FROM setlist_songs").getSingleResult()).isEqualTo(0L);
        //noinspection SqlResolve
        assertThat(em.createNativeQuery("SELECT COUNT(*) FROM setlist_vote_songs").getSingleResult()).isEqualTo(0L);
    }
}
