package com.loop.loop_backend.Setlist.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Setlist.domain.HitGrade;
import com.loop.loop_backend.Setlist.domain.Setlist;
import com.loop.loop_backend.Setlist.domain.SetlistType;
import com.loop.loop_backend.Setlist.domain.SetlistVote;
import com.loop.loop_backend.Setlist.dto.SetlistResultResponse;
import com.loop.loop_backend.Setlist.dto.SetlistResultResponse.HitRate;
import com.loop.loop_backend.Setlist.dto.SetlistResultResponse.MissedSong;
import com.loop.loop_backend.Setlist.dto.SetlistResultResponse.ResultSong;
import com.loop.loop_backend.Setlist.repository.SetlistRepository;
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
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

// 적중률 산출·공연 후 결과(NO.60~62, 68) 요구사항:
// - 분모 = 실제 셋리스트 곡 수(같은 곡 중복 연주는 1회). 순서·앵코르 무시, 곡 단위 일치만.
// - 개인 = 내가 고른 곡 중 실제로 나온 곡, 전체 = 득표 상위 n곡 중 실제로 나온 곡, 평균 = 투표자 개인 적중률 평균.
// - 등급 4구간 0–49 / 50–69 / 70–84 / 85–100.
// - 실제 셋리스트: 팬 예상(상위 n) · 내 예상 · 아무도 예상하지 못한 곡(득표 0). 미출현 곡은 득표순.
// - 실제 셋리스트 저장 전이면 대기(ready=false). 수정하면 다음 조회부터 재산출. 비공개 공연 403.
@DataJpaTest
class SetlistResultServiceTest {

    @Autowired private SetlistRepository setlistRepository;
    @Autowired private SetlistVoteRepository voteRepository;
    @Autowired private ConcertRepository concertRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private SongRepository songRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    private SetlistResultService service;
    private Concert concert;   // n = 2
    private Song s1, s2, s3, s4;
    private User me, a, b;

    @BeforeEach
    void setUp() {
        service = new SetlistResultService(setlistRepository, voteRepository, concertRepository, songRepository);
        Artist yuuri = artistRepository.save(Artist.builder().name("Yuuri").build());
        concert = concertRepository.save(Concert.builder()
                .title("YUURI LIVE").category(ConcertCategory.J_POP_ARTIST).artist(yuuri).expectedSongCount(2)
                .published(true).startDate(LocalDate.of(2026, 11, 20)).endDate(LocalDate.of(2026, 11, 20))
                .build());
        s1 = song(yuuri, "ドライフラワー", 1);
        s2 = song(yuuri, "ベテルギウス", 2);
        s3 = song(yuuri, "いかないで", 3);
        s4 = song(yuuri, "カバー曲", 4);
        me = user("me");
        a = user("a");
        b = user("b");
    }

    private Song song(Artist artist, String title, int sortOrder) {
        return songRepository.save(Song.builder().artist(artist).titleOriginal(title).sortOrder(sortOrder).build());
    }

    private User user(String providerId) {
        return userRepository.save(User.builder().authProvider(AuthProvider.KAKAO).providerId(providerId)
                .status(Status.ACTIVE).onboardingCompleted(true).build());
    }

    private void vote(User user, Song... songs) {
        voteRepository.save(SetlistVote.builder().concert(concert).user(user).songs(Set.of(songs)).build());
    }

    private void actual(Song... songs) {
        setlistRepository.save(Setlist.builder().concert(concert).type(SetlistType.ACTUAL).songs(List.of(songs)).build());
        em.flush();
        em.clear();
    }

    // 득표: s1 2표(me·a), s3 2표(a·b), s2 1표(me) → 순위 s1, s3, s2 → 팬 예상(상위 2) = {s1, s3}
    // 실제: s1, s4, s1(재연주), s2 → 분모 3({s1, s4, s2})
    private void standardScenario() {
        vote(me, s1, s2);
        vote(a, s1, s3);
        vote(b, s3);
        actual(s1, s4, s1, s2);
    }

    @Test
    void 개인_전체_평균_적중률을_중복_연주는_1회로_세서_계산한다() {
        standardScenario();

        SetlistResultResponse res = service.result(concert.getId(), me.getId());

        assertThat(res.ready()).isTrue();
        assertThat(res.participantCount()).isEqualTo(3);
        assertThat(res.overall()).isEqualTo(new HitRate(1, 3, 33, HitGrade.LOW));   // 팬 예상 {s1, s3} 중 s1
        assertThat(res.mine()).isEqualTo(new HitRate(2, 3, 67, HitGrade.MID));      // {s1, s2} 둘 다
        assertThat(res.averagePercent()).isEqualTo(33);                             // (66.7 + 33.3 + 0) / 3
    }

    @Test
    void 실제_셋리스트는_공연_순서대로_팬_예상_내_예상_아무도_예상_못한_곡을_표시한다() {
        standardScenario();

        SetlistResultResponse res = service.result(concert.getId(), me.getId());

        assertThat(res.songs()).extracting(ResultSong::position, ResultSong::titleOriginal,
                        ResultSong::fanPredicted, ResultSong::mine, ResultSong::unexpected)
                .containsExactly(
                        tuple(1, "ドライフラワー", true, true, false),
                        tuple(2, "カバー曲", false, false, true),
                        tuple(3, "ドライフラワー", true, true, false),
                        tuple(4, "ベテルギウス", false, true, false));
    }

    @Test
    void 예상했지만_나오지_않은_곡은_득표순이다() {
        Song s5 = song(concert.getArtist(), "未発表", 5);
        vote(me, s1, s5);
        vote(a, s3, s5);
        vote(b, s3);
        actual(s1);

        SetlistResultResponse res = service.result(concert.getId(), me.getId());

        // s3 2표, s5 2표(정렬 순번 3 < 5) → s3, s5
        assertThat(res.missedSongs()).extracting(MissedSong::titleOriginal, MissedSong::votes,
                        MissedSong::fanPredicted, MissedSong::mine)
                .containsExactly(tuple("いかないで", 2L, true, false), tuple("未発表", 2L, true, true));
    }

    @Test
    void 비로그인이나_미투표면_내_적중률은_없고_팬_기준만_준다() {
        standardScenario();
        User stranger = user("stranger");

        for (Long userId : new Long[]{null, stranger.getId()}) {
            SetlistResultResponse res = service.result(concert.getId(), userId);
            assertThat(res.mine()).isNull();
            assertThat(res.overall().percent()).isEqualTo(33);
            assertThat(res.songs()).noneMatch(ResultSong::mine);
            assertThat(res.missedSongs()).noneMatch(MissedSong::mine);
        }
    }

    @Test
    void 실제_셋리스트를_고치면_다음_조회부터_재산출된다() {
        standardScenario();
        Setlist actual = setlistRepository.findByConcertIdAndType(concert.getId(), SetlistType.ACTUAL).orElseThrow();
        actual.replaceSongs(List.of(em.find(Song.class, s1.getId()), em.find(Song.class, s3.getId())));
        em.flush();
        em.clear();

        SetlistResultResponse res = service.result(concert.getId(), me.getId());

        assertThat(res.overall()).isEqualTo(new HitRate(2, 2, 100, HitGrade.TOP));
        assertThat(res.mine()).isEqualTo(new HitRate(1, 2, 50, HitGrade.MID));
    }

    @Test
    void 실제_셋리스트가_없으면_대기_상태다() {
        vote(me, s1);
        em.flush();

        SetlistResultResponse res = service.result(concert.getId(), me.getId());

        assertThat(res.ready()).isFalse();
        assertThat(res.participantCount()).isEqualTo(1);
        assertThat(res.overall()).isNull();
        assertThat(res.mine()).isNull();
        assertThat(res.songs()).isEmpty();
    }

    @Test
    void 투표가_없으면_팬_적중률_0이고_평균은_없으며_모든_곡이_아무도_예상하지_못한_곡이다() {
        actual(s1, s2);

        SetlistResultResponse res = service.result(concert.getId(), null);

        assertThat(res.overall()).isEqualTo(new HitRate(0, 2, 0, HitGrade.LOW));
        assertThat(res.averagePercent()).isNull();
        assertThat(res.songs()).allMatch(ResultSong::unexpected);
    }

    @Test
    void 비공개_공연은_403() {
        Concert hidden = concertRepository.save(Concert.builder().title("숨김").category(ConcertCategory.J_POP_ARTIST)
                .artist(concert.getArtist()).expectedSongCount(2).build());

        assertThatThrownBy(() -> service.result(hidden.getId(), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONCERT_NOT_OPEN);
    }

    @Test
    void 등급은_4구간이고_적중률은_반올림한다() {
        assertThat(HitGrade.of(0)).isEqualTo(HitGrade.LOW);
        assertThat(HitGrade.of(49)).isEqualTo(HitGrade.LOW);
        assertThat(HitGrade.of(50)).isEqualTo(HitGrade.MID);
        assertThat(HitGrade.of(69)).isEqualTo(HitGrade.MID);
        assertThat(HitGrade.of(70)).isEqualTo(HitGrade.HIGH);
        assertThat(HitGrade.of(84)).isEqualTo(HitGrade.HIGH);
        assertThat(HitGrade.of(85)).isEqualTo(HitGrade.TOP);
        assertThat(HitGrade.of(100)).isEqualTo(HitGrade.TOP);
        // 2/3 = 66.7 → 67, 1/8 = 12.5 → 13
        assertThat(HitRate.of(2, 3).percent()).isEqualTo(67);
        assertThat(HitRate.of(1, 8).percent()).isEqualTo(13);
    }
}
