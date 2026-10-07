package com.loop.loop_backend.Setlist.domain;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Setlist.repository.SetlistRepository;
import com.loop.loop_backend.Setlist.repository.SetlistVoteRepository;
import com.loop.loop_backend.Setlist.repository.SongVoteCount;
import com.loop.loop_backend.Song.domain.Song;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
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

// 셋리스트(AD-07)·투표(NO.65·66) 저장 규칙:
// - 단독 공연에만, (공연, 구분)당 1건. 지난 셋리스트는 날짜·장소 필수.
// - 곡은 순서대로, 같은 곡 중복(앵코르)도 저장된다. 교체는 통째로.
// - 투표는 공연당 유저 1건, 득표 집계는 득표순 → 곡 정렬 순번 순.
// - 공연이 지워지면 셋리스트·투표도 지워진다.
@DataJpaTest
class SetlistPersistenceTest {

    @Autowired private SetlistRepository setlistRepository;
    @Autowired private SetlistVoteRepository voteRepository;
    @Autowired private ConcertRepository concertRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private EntityManager em;

    private Concert concert;
    private Song s1, s2, s3;   // 정렬 순번 1, 2, 3

    @BeforeEach
    void setUp() {
        Artist yuuri = artistRepository.save(Artist.builder().name("Yuuri").build());
        concert = concertRepository.save(Concert.builder()
                .title("YUURI LIVE").category(ConcertCategory.J_POP_ARTIST).artist(yuuri)
                .startDate(LocalDate.of(2026, 11, 20)).endDate(LocalDate.of(2026, 11, 20))
                .build());
        s1 = song(yuuri, "ドライフラワー", 1);
        s2 = song(yuuri, "ベテルギウス", 2);
        s3 = song(yuuri, "いかないで", 3);
    }

    private Song song(Artist artist, String title, int sortOrder) {
        Song song = Song.builder().artist(artist).titleOriginal(title).sortOrder(sortOrder).build();
        em.persist(song);
        return song;
    }

    private User user(String providerId) {
        User user = User.builder().authProvider(AuthProvider.KAKAO).providerId(providerId)
                .status(Status.ACTIVE).onboardingCompleted(true).build();
        em.persist(user);
        return user;
    }

    private Setlist actual(List<Song> songs) {
        return setlistRepository.save(Setlist.builder().concert(concert).type(SetlistType.ACTUAL).songs(songs).build());
    }

    private SetlistVote vote(User user, Song... songs) {
        return voteRepository.save(SetlistVote.builder().concert(concert).user(user).songs(Set.of(songs)).build());
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    @Test
    void 페스티벌에는_셋리스트를_등록할_수_없다() {
        Concert festival = concertRepository.save(Concert.builder()
                .title("SUMMER SONIC").category(ConcertCategory.JAPAN_FESTIVAL).build());

        assertThatThrownBy(() -> Setlist.builder().concert(festival).type(SetlistType.ACTUAL).songs(List.of(s1)).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 지난_셋리스트는_날짜_장소가_필수고_실제_셋리스트는_헤더를_비운다() {
        assertThatThrownBy(() -> Setlist.builder().concert(concert).type(SetlistType.RECENT)
                .performedOn(LocalDate.of(2026, 5, 1)).songs(List.of(s1)).build())
                .isInstanceOf(IllegalArgumentException.class);

        Setlist actual = Setlist.builder().concert(concert).type(SetlistType.ACTUAL)
                .tourName("무시됨").performedOn(LocalDate.of(2026, 5, 1)).venueName("무시됨").songs(List.of(s1)).build();
        assertThat(actual.getTourName()).isNull();
        assertThat(actual.getVenueName()).isNull();
    }

    @Test
    void 곡이_없으면_저장할_수_없다() {
        assertThatThrownBy(() -> actual(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 같은_공연_같은_구분은_1건이다() {
        actual(List.of(s1));
        assertThatThrownBy(() -> {
            actual(List.of(s2));
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 곡은_순서대로_저장되고_중복도_남으며_교체는_통째로다() {
        Setlist setlist = actual(List.of(s2, s1, s2));
        flushAndClear();

        Setlist loaded = setlistRepository.findByConcertIdAndType(concert.getId(), SetlistType.ACTUAL).orElseThrow();
        assertThat(loaded.getSongs()).extracting(Song::getId).containsExactly(s2.getId(), s1.getId(), s2.getId());

        loaded.replaceSongs(List.of(em.find(Song.class, s3.getId())));
        flushAndClear();

        assertThat(setlistRepository.findById(setlist.getId()).orElseThrow().getSongs())
                .extracting(Song::getId).containsExactly(s3.getId());
    }

    @Test
    void 결과_메일은_실제_셋리스트에서_한_번만_보낼_차례가_된다() {
        Setlist actual = actual(List.of(s1));
        Setlist recent = setlistRepository.save(Setlist.builder().concert(concert).type(SetlistType.RECENT)
                .performedOn(LocalDate.of(2026, 5, 1)).venueName("Zepp").songs(List.of(s1)).build());
        LocalDateTime now = LocalDateTime.of(2026, 11, 21, 12, 0);

        assertThat(actual.markResultMailSent(now)).isTrue();
        assertThat(actual.markResultMailSent(now)).isFalse();
        assertThat(recent.markResultMailSent(now)).isFalse();
    }

    @Test
    void 투표는_공연당_유저_1건이다() {
        User me = user("me");
        vote(me, s1);
        assertThatThrownBy(() -> {
            vote(me, s2);
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 득표_집계는_득표순이고_동점이면_곡_정렬_순번_순이다() {
        vote(user("a"), s3, s2);
        vote(user("b"), s3, s1);
        vote(user("c"), s2);
        flushAndClear();

        // s3: 2표, s2: 2표, s1: 1표 → s3·s2 동점은 정렬 순번(2 < 3)으로 s2가 앞
        assertThat(voteRepository.countVotesBySong(concert.getId())).containsExactly(
                new SongVoteCount(s2.getId(), 2), new SongVoteCount(s3.getId(), 2), new SongVoteCount(s1.getId(), 1));
        assertThat(voteRepository.countByConcertId(concert.getId())).isEqualTo(3);
    }

    @Test
    void 소프트_삭제된_곡은_득표_집계에서_빠진다() {
        vote(user("a"), s1, s2);
        flushAndClear();

        em.remove(em.find(Song.class, s1.getId()));
        flushAndClear();

        assertThat(voteRepository.countVotesBySong(concert.getId()))
                .extracting(SongVoteCount::songId).containsExactly(s2.getId());
    }

    @Test
    void 투표_수정은_곡_선택을_통째로_교체한다() {
        User me = user("me");
        vote(me, s1, s2);
        flushAndClear();

        SetlistVote mine = voteRepository.findByConcertIdAndUserId(concert.getId(), me.getId()).orElseThrow();
        mine.replaceSongs(Set.of(em.find(Song.class, s3.getId())));
        flushAndClear();

        assertThat(voteRepository.findByConcertIdAndUserId(concert.getId(), me.getId()).orElseThrow().getSongs())
                .extracting(Song::getId).containsExactly(s3.getId());
    }

    @Test
    void 탈퇴_정리는_그_유저의_투표만_지운다() {
        User me = user("me");
        vote(me, s1);
        vote(user("other"), s2);
        flushAndClear();

        voteRepository.deleteAllByUser(em.find(User.class, me.getId()));
        flushAndClear();

        assertThat(voteRepository.countByConcertId(concert.getId())).isEqualTo(1);
    }

    @Test
    void 공연이_지워지면_셋리스트와_투표도_지워진다() {
        actual(List.of(s1, s2));
        vote(user("a"), s1);
        flushAndClear();

        em.createNativeQuery("DELETE FROM concerts WHERE id = :id").setParameter("id", concert.getId()).executeUpdate();

        assertThat(setlistRepository.count()).isZero();
        assertThat(voteRepository.count()).isZero();
        assertThat(em.createNativeQuery("SELECT COUNT(*) FROM setlist_songs").getSingleResult()).isEqualTo(0L);
        assertThat(em.createNativeQuery("SELECT COUNT(*) FROM setlist_vote_songs").getSingleResult()).isEqualTo(0L);
    }
}
