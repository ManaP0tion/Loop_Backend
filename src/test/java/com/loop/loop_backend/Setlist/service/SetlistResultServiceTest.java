package com.loop.loop_backend.Setlist.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Performance.domain.Performance;
import com.loop.loop_backend.Performance.repository.PerformanceRepository;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

// 적중률 산출·공연 후 결과(NO.60~62, 68) 요구사항 - 출연 정보(Performance) 단위:
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
    @Autowired private PerformanceRepository performanceRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private SongRepository songRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    private SetlistResultService service;
    private Artist yuuri;
    private Concert concert;
    private Performance performance;   // n = 2
    private Song s1, s2, s3, s4;
    private User me, a, b;

    @BeforeEach
    void setUp() {
        service = new SetlistResultService(setlistRepository, voteRepository, performanceRepository);
        yuuri = artistRepository.save(Artist.builder().name("Yuuri").build());
        concert = saveSoloConcert(true);
        performance = Performance.solo(concert, yuuri);
        performance.changeExpectedSongCount(2);
        performanceRepository.save(performance);
        s1 = song("ドライフラワー", 1);
        s2 = song("ベテルギウス", 2);
        s3 = song("いかないで", 3);
        s4 = song("カバー曲", 4);
        me = user("me");
        a = user("a");
        b = user("b");
    }

    private Concert saveSoloConcert(boolean published) {
        return concertRepository.save(Concert.builder()
                .title("YUURI LIVE").category(ConcertCategory.STAGE).artist(yuuri)
                .published(published).startDate(LocalDate.of(2026, 11, 20)).endDate(LocalDate.of(2026, 11, 20))
                .build());
    }

    private Song song(String title, int sortOrder) {
        return songRepository.save(Song.builder().artist(yuuri).titleOriginal(title).sortOrder(sortOrder).build());
    }

    private User user(String providerId) {
        return userRepository.save(User.builder().authProvider(AuthProvider.KAKAO).providerId(providerId)
                .status(Status.ACTIVE).onboardingCompleted(true).build());
    }

    private void vote(User user, Song... songs) {
        voteRepository.save(SetlistVote.builder().performance(performance).user(user).songs(Set.of(songs)).build());
    }

    private void actual(Song... songs) {
        setlistRepository.save(Setlist.builder().performance(performance).type(SetlistType.ACTUAL)
                .songs(List.of(songs)).build());
        em.flush();
        em.clear();
    }

    private SetlistResultResponse result(Long userId) {
        return service.result(concert.getId(), performance.getId(), userId);
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
    @DisplayName("개인·전체·평균 적중률을 계산하고 같은 곡 중복 연주는 1회로 센다")
    void result_WithVotesAndActual_CalculatesHitRatesCountingDuplicatesOnce() {
        // given
        standardScenario();

        // when
        SetlistResultResponse res = result(me.getId());

        // then
        assertThat(res.ready()).isTrue();
        assertThat(res.participantCount()).isEqualTo(3);
        assertThat(res.overall()).isEqualTo(new HitRate(1, 3, 33, HitGrade.LOW));   // 팬 예상 {s1, s3} 중 s1
        assertThat(res.mine()).isEqualTo(new HitRate(2, 3, 67, HitGrade.MID));      // {s1, s2} 둘 다
        assertThat(res.averagePercent()).isEqualTo(33);                             // (66.7 + 33.3 + 0) / 3
        assertThat(res.myTopPercent()).isEqualTo(34);                               // 3명 중 1위 → ceil(100/3)
        assertThat(result(a.getId()).myTopPercent()).isEqualTo(67);                 // 2위 → ceil(200/3)
    }

    @Test
    @DisplayName("실제 셋리스트는 공연 순서대로 팬 예상, 내 예상, 아무도 예상하지 못한 곡을 표시한다")
    void result_ActualSongs_MarksFanPredictedMineAndUnexpected() {
        // given
        standardScenario();

        // when
        SetlistResultResponse res = result(me.getId());

        // then
        assertThat(res.songs()).extracting(ResultSong::position, ResultSong::titleOriginal,
                        ResultSong::fanPredicted, ResultSong::mine, ResultSong::unexpected)
                .containsExactly(
                        tuple(1, "ドライフラワー", true, true, false),
                        tuple(2, "カバー曲", false, false, true),
                        tuple(3, "ドライフラワー", true, true, false),
                        tuple(4, "ベテルギウス", false, true, false));
    }

    @Test
    @DisplayName("예상했지만 나오지 않은 곡은 득표순이고 동점은 정렬 순번 순이다")
    void result_MissedSongs_OrderedByVotesThenSortOrder() {
        // given
        Song s5 = song("未発表", 5);
        vote(me, s1, s5);
        vote(a, s3, s5);
        vote(b, s3);
        actual(s1);

        // when
        SetlistResultResponse res = result(me.getId());

        // then - s3 2표, s5 2표(정렬 순번 3 < 5) → s3, s5
        assertThat(res.missedSongs()).extracting(MissedSong::titleOriginal, MissedSong::votes,
                        MissedSong::fanPredicted, MissedSong::mine)
                .containsExactly(tuple("いかないで", 2L, true, false), tuple("未発表", 2L, true, true));
    }

    @Test
    @DisplayName("비로그인이거나 투표하지 않았으면 내 적중률 없이 팬 기준만 준다")
    void result_WhenAnonymousOrNotVoted_ReturnsFanBasedOnly() {
        // given
        standardScenario();
        User stranger = user("stranger");

        // when & then
        for (Long userId : new Long[]{null, stranger.getId()}) {
            SetlistResultResponse res = result(userId);
            assertThat(res.mine()).isNull();
            assertThat(res.myTopPercent()).isNull();
            assertThat(res.overall().percent()).isEqualTo(33);
            assertThat(res.songs()).noneMatch(ResultSong::mine);
            assertThat(res.missedSongs()).noneMatch(MissedSong::mine);
        }
    }

    @Test
    @DisplayName("실제 셋리스트를 고치면 다음 조회부터 적중률이 재산출된다")
    void result_WhenActualSetlistEdited_RecalculatesOnNextRead() {
        // given
        standardScenario();
        Setlist actual = setlistRepository.findByPerformanceIdAndType(performance.getId(), SetlistType.ACTUAL).orElseThrow();
        actual.replaceSongs(List.of(em.find(Song.class, s1.getId()), em.find(Song.class, s3.getId())));
        em.flush();
        em.clear();

        // when
        SetlistResultResponse res = result(me.getId());

        // then
        assertThat(res.overall()).isEqualTo(new HitRate(2, 2, 100, HitGrade.TOP));
        assertThat(res.mine()).isEqualTo(new HitRate(1, 2, 50, HitGrade.MID));
    }

    @Test
    @DisplayName("실제 셋리스트가 없으면 대기 상태다")
    void result_WhenNoActualSetlist_ReturnsWaiting() {
        // given
        vote(me, s1);
        em.flush();

        // when
        SetlistResultResponse res = result(me.getId());

        // then
        assertThat(res.ready()).isFalse();
        assertThat(res.participantCount()).isEqualTo(1);
        assertThat(res.overall()).isNull();
        assertThat(res.mine()).isNull();
        assertThat(res.songs()).isEmpty();
    }

    @Test
    @DisplayName("투표가 없으면 팬 적중률 0, 평균 없음이고 모든 곡이 아무도 예상하지 못한 곡이다")
    void result_WhenNoVotes_AllSongsUnexpected() {
        // given
        actual(s1, s2);

        // when
        SetlistResultResponse res = result(null);

        // then
        assertThat(res.overall()).isEqualTo(new HitRate(0, 2, 0, HitGrade.LOW));
        assertThat(res.averagePercent()).isNull();
        assertThat(res.songs()).allMatch(ResultSong::unexpected);
    }

    @Test
    @DisplayName("페스티벌 헤드라이너도 출연 정보별로 결과를 계산한다")
    void result_WhenFestivalHeadliner_CalculatesPerPerformance() {
        // given
        Concert festival = concertRepository.save(Concert.builder()
                .title("SUMMER SONIC").category(ConcertCategory.FESTIVAL).published(true)
                .startDate(LocalDate.of(2026, 11, 20)).endDate(LocalDate.of(2026, 11, 21))
                .build());
        Performance headliner = Performance.builder().concert(festival).artist(yuuri).day(1).displayOrder(1).build();
        headliner.changeHeadliner(true);
        performanceRepository.save(headliner);
        voteRepository.save(SetlistVote.builder().performance(headliner).user(me).songs(Set.of(s1)).build());
        setlistRepository.save(Setlist.builder().performance(headliner).type(SetlistType.ACTUAL)
                .songs(List.of(s1, s2)).build());
        em.flush();
        em.clear();

        // when
        SetlistResultResponse res = service.result(festival.getId(), headliner.getId(), me.getId());

        // then
        assertThat(res.ready()).isTrue();
        assertThat(res.participantCount()).isEqualTo(1);
        assertThat(res.mine()).isEqualTo(new HitRate(1, 2, 50, HitGrade.MID));
    }

    @Test
    @DisplayName("비공개 공연의 결과를 조회하면 403")
    void result_WhenConcertHidden_ThrowsConcertNotOpen() {
        // given
        Concert hidden = saveSoloConcert(false);
        Performance hiddenPerformance = performanceRepository.save(Performance.solo(hidden, yuuri));

        // when & then
        assertThatThrownBy(() -> service.result(hidden.getId(), hiddenPerformance.getId(), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONCERT_NOT_OPEN);
    }

    @Test
    @DisplayName("공연 뒤 곡이 삭제돼도 적중률·실제 셋리스트·미출현 곡이 그대로이고 삭제 표시만 붙는다")
    void result_WhenSongsDeletedAfterConcert_KeepsResultAndMarksDeleted() {
        // given - 실제 셋리스트에 있던 s2, 투표만 받은 미출현 곡 s3를 공연 뒤 삭제
        standardScenario();
        SetlistResultResponse before = result(me.getId());
        songRepository.delete(songRepository.findById(s2.getId()).orElseThrow());
        songRepository.delete(songRepository.findById(s3.getId()).orElseThrow());
        em.flush();
        em.clear();

        // when
        SetlistResultResponse after = result(me.getId());

        // then
        assertThat(after.overall()).isEqualTo(before.overall());
        assertThat(after.mine()).isEqualTo(before.mine());
        assertThat(after.averagePercent()).isEqualTo(before.averagePercent());
        assertThat(after.songs()).extracting(ResultSong::position, ResultSong::songId, ResultSong::deleted)
                .containsExactly(tuple(1, s1.getId(), false), tuple(2, s4.getId(), false),
                        tuple(3, s1.getId(), false), tuple(4, s2.getId(), true));
        assertThat(after.missedSongs()).extracting(MissedSong::titleOriginal, MissedSong::votes, MissedSong::deleted)
                .containsExactly(tuple("いかないで", 2L, true));
    }

    @Test
    @DisplayName("등급은 4구간이고 적중률은 반올림한다")
    void hitGrade_ClassifiesIntoFourRangesAndRoundsPercent() {
        // when & then
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

    // ---------- 공연 단위 내 적중률(페스티벌 헤드라이너 합산) ----------

    private Performance headliner(Concert festival, Artist artist, int order) {
        Performance p = Performance.builder().concert(festival).artist(artist).day(1).displayOrder(order).build();
        p.changeHeadliner(true);
        return performanceRepository.save(p);
    }

    private void vote(Performance p, User user, Song... songs) {
        voteRepository.save(SetlistVote.builder().performance(p).user(user).songs(Set.of(songs)).build());
    }

    private void actual(Performance p, Song... songs) {
        setlistRepository.save(Setlist.builder().performance(p).type(SetlistType.ACTUAL).songs(List.of(songs)).build());
    }

    private Song song(Artist artist, String title, int sortOrder) {
        return songRepository.save(Song.builder().artist(artist).titleOriginal(title).sortOrder(sortOrder).build());
    }

    @Test
    @DisplayName("페스티벌 내 적중률은 투표한 헤드라이너의 적중 곡 수 합 ÷ 실제 곡 수 합이고, 실제 셋리스트 없는 아티스트는 뺀다")
    void concertResult_WithSeveralHeadliners_SumsHitsOverSumOfActualSongs() {
        // given: Yuuri 실제 3곡, Vaundy 실제 2곡, Aimer 실제 셋리스트 없음
        Concert festival = concertRepository.save(Concert.builder()
                .title("SUMMER SONIC").category(ConcertCategory.FESTIVAL).published(true)
                .startDate(LocalDate.of(2026, 11, 20)).endDate(LocalDate.of(2026, 11, 21)).build());
        Artist vaundy = artistRepository.save(Artist.builder().name("Vaundy").build());
        Artist aimer = artistRepository.save(Artist.builder().name("Aimer").build());
        Performance p1 = headliner(festival, yuuri, 1);
        Performance p2 = headliner(festival, vaundy, 2);
        Performance p3 = headliner(festival, aimer, 3);
        Song t1 = song(vaundy, "怪獣の花唄", 1);
        Song t2 = song(vaundy, "踊り子", 2);
        Song u1 = song(aimer, "残響散歌", 1);
        actual(p1, s1, s2, s3);
        actual(p2, t1, t2);
        vote(p1, me, s1, s2);       // 2/3
        vote(p2, me, t1);           // 1/2
        vote(p3, me, u1);           // 실제 셋리스트 없음 → 제외. 합산 3/5 = 60%
        vote(p1, a, s1, s2, s3);    // 3/3 = 100%
        vote(p2, b, t2, t1);        // 2/2 = 100%
        vote(p1, user("c"), s4);    // 0/3 = 0%
        em.flush();
        em.clear();

        // when
        var res = service.concertResult(festival.getId(), me.getId());

        // then: 4명 중 나보다 높은 2명 → ceil(100 × 3 / 4) = 75
        assertThat(res.ready()).isTrue();
        assertThat(res.mine()).isEqualTo(new HitRate(3, 5, 60, HitGrade.MID));
        assertThat(res.myTopPercent()).isEqualTo(75);
        assertThat(service.concertResult(festival.getId(), null).mine()).isNull();
    }

    @Test
    @DisplayName("실제 셋리스트가 하나도 없으면 공연 단위 결과는 대기, 비공개 공연이면 403")
    void concertResult_WithoutActualOrUnpublished_WaitsOrThrows() {
        // given
        vote(me, s1);
        em.flush();

        // when & then
        assertThat(service.concertResult(concert.getId(), me.getId()).ready()).isFalse();
        Concert hidden = saveSoloConcert(false);
        performanceRepository.save(Performance.solo(hidden, yuuri));
        assertThatThrownBy(() -> service.concertResult(hidden.getId(), me.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONCERT_NOT_OPEN);
    }
}
