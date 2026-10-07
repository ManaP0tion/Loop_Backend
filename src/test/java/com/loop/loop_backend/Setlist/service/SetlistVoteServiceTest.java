package com.loop.loop_backend.Setlist.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Setlist.dto.MySetlistVoteResponse;
import com.loop.loop_backend.Setlist.dto.SetlistCandidatesResponse;
import com.loop.loop_backend.Setlist.dto.SongCandidateResponse;
import com.loop.loop_backend.Setlist.repository.SetlistVoteRepository;
import com.loop.loop_backend.Song.Repository.SongRepository;
import com.loop.loop_backend.Song.domain.Song;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 예상 셋리스트 후보(NO.64)·투표(NO.65) 요구사항:
// - 후보: 공연 아티스트의 삭제되지 않은 곡 전체, 정렬 순번대로. 최대 선택 수 n·마감 여부를 같이 준다.
// - 셋리스트를 운영하지 않는 공연(페스티벌·예상 곡 수 없음)은 빈 후보, 투표는 400. 비공개 공연은 403.
// - 투표: 공연당 유저 1건, 1~n곡(중복은 하나로), 공연 아티스트의 삭제되지 않은 곡만.
// - 마감 = 공연 시작일 00:00 KST. 직전까지 수정 가능, 그 시각부터 409. 수정은 통째 교체.
@DataJpaTest
class SetlistVoteServiceTest {

    private static final LocalDateTime BEFORE_DEADLINE = LocalDateTime.of(2026, 11, 19, 23, 59, 59);
    private static final LocalDateTime DEADLINE = LocalDateTime.of(2026, 11, 20, 0, 0);

    @Autowired private SetlistVoteRepository voteRepository;
    @Autowired private ConcertRepository concertRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private SongRepository songRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    private SetlistVoteService service;
    private Artist yuuri;
    private Concert concert;   // 2026-11-20 공연, n = 2
    private Song s1, s2, s3;
    private User me;

    @BeforeEach
    void setUp() {
        service = new SetlistVoteService(voteRepository, concertRepository, songRepository, userRepository);
        yuuri = artistRepository.save(Artist.builder().name("Yuuri").build());
        concert = saveConcert(ConcertCategory.J_POP_ARTIST, yuuri, 2, true);
        s3 = song(yuuri, "いかないで", 3);
        s1 = song(yuuri, "ドライフラワー", 1);
        s2 = song(yuuri, "ベテルギウス", 2);
        me = userRepository.save(User.builder().authProvider(AuthProvider.KAKAO).providerId("me")
                .status(Status.ACTIVE).onboardingCompleted(true).build());
    }

    private Concert saveConcert(ConcertCategory category, Artist artist, Integer n, boolean published) {
        return concertRepository.save(Concert.builder()
                .title("YUURI LIVE").category(category).artist(artist).expectedSongCount(n).published(published)
                .startDate(LocalDate.of(2026, 11, 20)).endDate(LocalDate.of(2026, 11, 20))
                .build());
    }

    private Song song(Artist artist, String title, int sortOrder) {
        return songRepository.save(Song.builder().artist(artist).titleOriginal(title)
                .titleRomanized("romaji-" + sortOrder).sortOrder(sortOrder).build());
    }

    private MySetlistVoteResponse vote(LocalDateTime now, Long... songIds) {
        return service.saveVote(concert.getId(), me.getId(), List.of(songIds), now);
    }

    private static void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    // ---------- 후보 ----------

    @Test
    void 후보는_아티스트의_곡_전체를_정렬_순번대로_주고_n과_마감_여부를_같이_준다() {
        Artist other = artistRepository.save(Artist.builder().name("Ado").build());
        song(other, "うっせぇわ", 1);
        songRepository.delete(s3);
        em.flush();
        em.clear();

        SetlistCandidatesResponse res = service.candidates(concert.getId(), BEFORE_DEADLINE);

        assertThat(res.maxSelect()).isEqualTo(2);
        assertThat(res.votingClosed()).isFalse();
        assertThat(res.songs()).extracting(SongCandidateResponse::titleOriginal).containsExactly("ドライフラワー", "ベテルギウス");
        assertThat(res.songs().get(0).titleRomanized()).isEqualTo("romaji-1");
        assertThat(service.candidates(concert.getId(), DEADLINE).votingClosed()).isTrue();
    }

    @Test
    void 셋리스트를_운영하지_않는_공연은_빈_후보다() {
        Concert noCount = saveConcert(ConcertCategory.J_POP_ARTIST, yuuri, null, true);
        Concert festival = saveConcert(ConcertCategory.JAPAN_FESTIVAL, null, null, true);

        assertThat(service.candidates(noCount.getId(), BEFORE_DEADLINE).songs()).isEmpty();
        assertThat(service.candidates(noCount.getId(), BEFORE_DEADLINE).maxSelect()).isNull();
        assertThat(service.candidates(festival.getId(), BEFORE_DEADLINE).songs()).isEmpty();
    }

    @Test
    void 비공개_공연은_403_없는_공연은_404() {
        Concert hidden = saveConcert(ConcertCategory.J_POP_ARTIST, yuuri, 2, false);

        assertErrorCode(() -> service.candidates(hidden.getId(), BEFORE_DEADLINE), ErrorCode.CONCERT_NOT_OPEN);
        assertErrorCode(() -> service.saveVote(hidden.getId(), me.getId(), List.of(s1.getId()), BEFORE_DEADLINE),
                ErrorCode.CONCERT_NOT_OPEN);
        assertErrorCode(() -> service.candidates(9999L, BEFORE_DEADLINE), ErrorCode.CONCERT_NOT_FOUND);
    }

    // ---------- 투표 ----------

    @Test
    void 투표하면_정렬_순번대로_내_선택을_돌려준다() {
        MySetlistVoteResponse res = vote(BEFORE_DEADLINE, s2.getId(), s1.getId());

        assertThat(res.voted()).isTrue();
        assertThat(res.songIds()).containsExactly(s1.getId(), s2.getId());
        assertThat(res.resultMailConsent()).isFalse();
    }

    @Test
    void 수정은_곡_선택을_통째로_교체하고_1건으로_유지된다() {
        vote(BEFORE_DEADLINE, s1.getId(), s2.getId());
        em.flush();
        em.clear();

        vote(BEFORE_DEADLINE, s3.getId());
        em.flush();
        em.clear();

        assertThat(service.myVote(concert.getId(), me.getId()).songIds()).containsExactly(s3.getId());
        assertThat(voteRepository.countByConcertId(concert.getId())).isEqualTo(1);
    }

    @Test
    void 마감_직전까지는_되고_마감_시각부터_409() {
        assertThat(vote(BEFORE_DEADLINE, s1.getId()).voted()).isTrue();
        assertErrorCode(() -> vote(DEADLINE, s2.getId()), ErrorCode.SETLIST_VOTE_CLOSED);
        assertErrorCode(() -> vote(DEADLINE.plusDays(3), s2.getId()), ErrorCode.SETLIST_VOTE_CLOSED);
    }

    @Test
    void 시작일이_미정이면_마감이_없다() {
        Concert undated = concertRepository.save(Concert.builder().title("미정").category(ConcertCategory.J_POP_ARTIST)
                .artist(yuuri).expectedSongCount(2).published(true).build());

        assertThat(service.saveVote(undated.getId(), me.getId(), List.of(s1.getId()), DEADLINE.plusYears(1)).voted()).isTrue();
    }

    @Test
    void 곡은_1곡_이상_n곡_이하이고_중복은_하나로_센다() {
        assertThatThrownBy(() -> vote(BEFORE_DEADLINE, s1.getId(), s2.getId(), s3.getId()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.saveVote(concert.getId(), me.getId(), List.of(), BEFORE_DEADLINE))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(vote(BEFORE_DEADLINE, s1.getId(), s2.getId(), s1.getId()).songIds()).containsExactly(s1.getId(), s2.getId());
    }

    @Test
    void 다른_아티스트의_곡이나_삭제된_곡은_400() {
        Artist other = artistRepository.save(Artist.builder().name("Ado").build());
        Song otherSong = song(other, "うっせぇわ", 1);
        songRepository.delete(s3);
        em.flush();
        em.clear();

        assertThatThrownBy(() -> vote(BEFORE_DEADLINE, otherSong.getId())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> vote(BEFORE_DEADLINE, s3.getId())).isInstanceOf(IllegalArgumentException.class);
        assertThat(voteRepository.count()).isZero();
    }

    @Test
    void 셋리스트를_운영하지_않는_공연에는_투표할_수_없다() {
        Concert noCount = saveConcert(ConcertCategory.J_POP_ARTIST, yuuri, null, true);

        assertThatThrownBy(() -> service.saveVote(noCount.getId(), me.getId(), List.of(s1.getId()), BEFORE_DEADLINE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 투표하지_않았으면_빈_응답이다() {
        MySetlistVoteResponse res = service.myVote(concert.getId(), me.getId());

        assertThat(res.voted()).isFalse();
        assertThat(res.songIds()).isEmpty();
    }
}
