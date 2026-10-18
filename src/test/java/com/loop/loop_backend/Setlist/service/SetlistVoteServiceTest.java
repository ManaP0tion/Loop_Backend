package com.loop.loop_backend.Setlist.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Performance.domain.Performance;
import com.loop.loop_backend.Performance.repository.PerformanceRepository;
import com.loop.loop_backend.Setlist.dto.MySetlistVoteResponse;
import com.loop.loop_backend.Setlist.dto.SetlistCandidatesResponse;
import com.loop.loop_backend.Setlist.dto.SetlistRankingResponse;
import com.loop.loop_backend.Setlist.dto.SetlistRankingResponse.RankedSong;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

// 예상 셋리스트 후보(NO.64)·투표(NO.65) 요구사항 - 출연 정보(Performance) 단위:
// - 후보: 출연 아티스트의 삭제되지 않은 곡 전체, 정렬 순번대로. 최대 선택 수 n·마감 여부를 같이 준다.
// - 단독 공연은 출연 정보 1건, 페스티벌은 헤드라이너만 셋리스트를 운영한다. 비헤드라이너는 빈 후보, 투표 400. 비공개 공연은 403.
// - 투표: 출연 정보당 유저 1건, 1~n곡(중복은 하나로), 출연 아티스트의 삭제되지 않은 곡만.
// - 마감 = 공연 시작일 00:00 KST(페스티벌은 DAY와 관계없이 시작일). 직전까지 수정 가능, 그 시각부터 409. 수정은 통째 교체.
@DataJpaTest
class SetlistVoteServiceTest {

    private static final LocalDateTime BEFORE_DEADLINE = LocalDateTime.of(2026, 11, 19, 23, 59, 59);
    private static final LocalDateTime DEADLINE = LocalDateTime.of(2026, 11, 20, 0, 0);

    @Autowired private SetlistVoteRepository voteRepository;
    @Autowired private ConcertRepository concertRepository;
    @Autowired private PerformanceRepository performanceRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private SongRepository songRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    private SetlistVoteService service;
    private Artist yuuri;
    private Concert concert;          // 2026-11-20 단독 공연
    private Performance performance;  // n = 2
    private Song s1, s2, s3;
    private User me;

    @BeforeEach
    void setUp() {
        service = new SetlistVoteService(voteRepository, performanceRepository, songRepository, userRepository);
        yuuri = artistRepository.save(Artist.builder().name("Yuuri").build());
        concert = saveSoloConcert(true);
        performance = saveSolo(concert, 2);
        s3 = song(yuuri, "いかないで", 3);
        s1 = song(yuuri, "ドライフラワー", 1);
        s2 = song(yuuri, "ベテルギウス", 2);
        me = user("me");
    }

    private Concert saveSoloConcert(boolean published) {
        return concertRepository.save(Concert.builder()
                .title("YUURI LIVE").category(ConcertCategory.J_POP_ARTIST).artist(yuuri).published(published)
                .startDate(LocalDate.of(2026, 11, 20)).endDate(LocalDate.of(2026, 11, 20))
                .build());
    }

    private Performance saveSolo(Concert concert, Integer n) {
        Performance solo = Performance.solo(concert, concert.getArtist());
        solo.changeExpectedSongCount(n);
        return performanceRepository.save(solo);
    }

    /** 2026-11-20 ~ 21 이틀 페스티벌 */
    private Concert saveFestival() {
        return concertRepository.save(Concert.builder()
                .title("SUMMER SONIC").category(ConcertCategory.JAPAN_FESTIVAL).published(true)
                .startDate(LocalDate.of(2026, 11, 20)).endDate(LocalDate.of(2026, 11, 21))
                .build());
    }

    private Performance saveLineup(Concert festival, Artist artist, int day, boolean headliner) {
        Performance lineup = Performance.builder().concert(festival).artist(artist).day(day).displayOrder(day).build();
        lineup.changeHeadliner(headliner);
        return performanceRepository.save(lineup);
    }

    private Song song(Artist artist, String title, int sortOrder) {
        return songRepository.save(Song.builder().artist(artist).titleOriginal(title)
                .titleRomanized("romaji-" + sortOrder).sortOrder(sortOrder).build());
    }

    private User user(String providerId) {
        return userRepository.save(User.builder().authProvider(AuthProvider.KAKAO).providerId(providerId)
                .status(Status.ACTIVE).onboardingCompleted(true).build());
    }

    private MySetlistVoteResponse vote(LocalDateTime now, Long... songIds) {
        return service.saveVote(concert.getId(), performance.getId(), me.getId(), List.of(songIds), now);
    }

    private void voteAs(User user, Long... songIds) {
        service.saveVote(concert.getId(), performance.getId(), user.getId(), List.of(songIds), BEFORE_DEADLINE);
    }

    private static void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    // ---------- 후보 ----------

    @Test
    @DisplayName("후보는 출연 아티스트의 곡 전체를 정렬 순번대로 주고 n과 마감 여부를 같이 준다")
    void candidates_WhenSoloPerformance_ReturnsArtistSongsInSortOrder() {
        // given
        Artist other = artistRepository.save(Artist.builder().name("Ado").build());
        song(other, "うっせぇわ", 1);
        songRepository.delete(s3);
        em.flush();
        em.clear();

        // when
        SetlistCandidatesResponse res = service.candidates(concert.getId(), performance.getId(), BEFORE_DEADLINE);

        // then
        assertThat(res.maxSelect()).isEqualTo(2);
        assertThat(res.votingClosed()).isFalse();
        assertThat(res.songs()).extracting(SongCandidateResponse::titleOriginal).containsExactly("ドライフラワー", "ベテルギウス");
        assertThat(res.songs().get(0).titleRomanized()).isEqualTo("romaji-1");
        assertThat(service.candidates(concert.getId(), performance.getId(), DEADLINE).votingClosed()).isTrue();
    }

    @Test
    @DisplayName("예상 곡 수가 없으면 최대 선택 곡 수는 기본값 20곡이다")
    void candidates_WhenExpectedSongCountMissing_UsesDefault20() {
        // given
        Concert noCount = saveSoloConcert(true);
        Performance noCountPerformance = saveSolo(noCount, null);

        // when
        SetlistCandidatesResponse res = service.candidates(noCount.getId(), noCountPerformance.getId(), BEFORE_DEADLINE);

        // then
        assertThat(res.songs()).isNotEmpty();
        assertThat(res.maxSelect()).isEqualTo(20);
    }

    @Test
    @DisplayName("페스티벌 헤드라이너 후보는 그 헤드라이너의 곡과 헤드라이너별 예상 곡 수를 준다")
    void candidates_WhenFestivalHeadliner_ReturnsHeadlinerSongs() {
        // given
        Concert festival = saveFestival();
        Artist ado = artistRepository.save(Artist.builder().name("Ado").build());
        song(ado, "うっせぇわ", 1);
        Performance headliner = saveLineup(festival, ado, 2, true);
        headliner.changeExpectedSongCount(12);

        // when
        SetlistCandidatesResponse res = service.candidates(festival.getId(), headliner.getId(), BEFORE_DEADLINE);

        // then
        assertThat(res.maxSelect()).isEqualTo(12);
        assertThat(res.songs()).extracting(SongCandidateResponse::titleOriginal).containsExactly("うっせぇわ");
    }

    @Test
    @DisplayName("페스티벌 비헤드라이너는 셋리스트를 운영하지 않아 빈 후보를 준다")
    void candidates_WhenFestivalNonHeadliner_ReturnsEmpty() {
        // given
        Concert festival = saveFestival();
        Performance nonHeadliner = saveLineup(festival, yuuri, 1, false);

        // when
        SetlistCandidatesResponse res = service.candidates(festival.getId(), nonHeadliner.getId(), BEFORE_DEADLINE);

        // then
        assertThat(res.songs()).isEmpty();
        assertThat(res.maxSelect()).isNull();
    }

    @Test
    @DisplayName("비공개 공연은 403, 없는 출연 정보와 다른 공연의 출연 정보는 404")
    void candidates_WhenHiddenOrNotFound_ThrowsError() {
        // given
        Concert hidden = saveSoloConcert(false);
        Performance hiddenPerformance = saveSolo(hidden, 2);
        Concert another = saveSoloConcert(true);

        // when & then
        assertErrorCode(() -> service.candidates(hidden.getId(), hiddenPerformance.getId(), BEFORE_DEADLINE),
                ErrorCode.CONCERT_NOT_OPEN);
        assertErrorCode(() -> service.saveVote(hidden.getId(), hiddenPerformance.getId(), me.getId(),
                List.of(s1.getId()), BEFORE_DEADLINE), ErrorCode.CONCERT_NOT_OPEN);
        assertErrorCode(() -> service.candidates(concert.getId(), 9999L, BEFORE_DEADLINE), ErrorCode.PERFORMANCE_NOT_FOUND);
        assertErrorCode(() -> service.candidates(another.getId(), performance.getId(), BEFORE_DEADLINE),
                ErrorCode.PERFORMANCE_NOT_FOUND);
    }

    // ---------- 투표 ----------

    @Test
    @DisplayName("투표하면 내 선택을 정렬 순번대로 돌려준다")
    void saveVote_WithValidSongs_ReturnsMySongsInSortOrder() {
        // when
        MySetlistVoteResponse res = vote(BEFORE_DEADLINE, s2.getId(), s1.getId());

        // then
        assertThat(res.voted()).isTrue();
        assertThat(res.songIds()).containsExactly(s1.getId(), s2.getId());
        assertThat(res.resultMailConsent()).isFalse();
    }

    @Test
    @DisplayName("투표 수정은 곡 선택을 통째로 교체하고 투표는 1건으로 유지된다")
    void saveVote_WhenAlreadyVoted_ReplacesSongs() {
        // given
        vote(BEFORE_DEADLINE, s1.getId(), s2.getId());
        em.flush();
        em.clear();

        // when
        vote(BEFORE_DEADLINE, s3.getId());
        em.flush();
        em.clear();

        // then
        assertThat(service.myVote(concert.getId(), performance.getId(), me.getId()).songIds()).containsExactly(s3.getId());
        assertThat(voteRepository.countByPerformanceId(performance.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("마감 직전까지는 투표되고 마감 시각부터는 409")
    void saveVote_AtDeadline_ThrowsVoteClosed() {
        // when & then
        assertThat(vote(BEFORE_DEADLINE, s1.getId()).voted()).isTrue();
        assertErrorCode(() -> vote(DEADLINE, s2.getId()), ErrorCode.SETLIST_VOTE_CLOSED);
        assertErrorCode(() -> vote(DEADLINE.plusDays(3), s2.getId()), ErrorCode.SETLIST_VOTE_CLOSED);
    }

    @Test
    @DisplayName("공연 시작일이 미정이면 투표 마감이 없다")
    void saveVote_WhenStartDateUndecided_NeverCloses() {
        // given
        Concert undated = concertRepository.save(Concert.builder().title("미정").category(ConcertCategory.J_POP_ARTIST)
                .artist(yuuri).published(true).build());
        Performance undatedPerformance = saveSolo(undated, 2);

        // when
        MySetlistVoteResponse res = service.saveVote(undated.getId(), undatedPerformance.getId(), me.getId(),
                List.of(s1.getId()), DEADLINE.plusYears(1));

        // then
        assertThat(res.voted()).isTrue();
    }

    @Test
    @DisplayName("곡은 1곡 이상 n곡 이하이고 중복은 하나로 센다")
    void saveVote_WithSongCountOutOfRange_ThrowsAndCountsDuplicatesOnce() {
        // when & then
        assertThatThrownBy(() -> vote(BEFORE_DEADLINE, s1.getId(), s2.getId(), s3.getId()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.saveVote(concert.getId(), performance.getId(), me.getId(), List.of(), BEFORE_DEADLINE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(vote(BEFORE_DEADLINE, s1.getId(), s2.getId(), s1.getId()).songIds()).containsExactly(s1.getId(), s2.getId());
    }

    @Test
    @DisplayName("다른 아티스트의 곡이나 삭제된 곡으로 투표하면 400")
    void saveVote_WithOtherArtistOrDeletedSong_ThrowsInvalidInput() {
        // given
        Artist other = artistRepository.save(Artist.builder().name("Ado").build());
        Song otherSong = song(other, "うっせぇわ", 1);
        songRepository.delete(s3);
        em.flush();
        em.clear();

        // when & then
        assertThatThrownBy(() -> vote(BEFORE_DEADLINE, otherSong.getId())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> vote(BEFORE_DEADLINE, s3.getId())).isInstanceOf(IllegalArgumentException.class);
        assertThat(voteRepository.count()).isZero();
    }

    @Test
    @DisplayName("페스티벌 비헤드라이너에는 투표할 수 없다")
    void saveVote_WhenFestivalNonHeadliner_ThrowsInvalidInput() {
        // given
        Concert festival = saveFestival();
        Performance nonHeadliner = saveLineup(festival, yuuri, 1, false);

        // when & then
        assertThatThrownBy(() -> service.saveVote(festival.getId(), nonHeadliner.getId(), me.getId(),
                List.of(s1.getId()), BEFORE_DEADLINE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("페스티벌은 헤드라이너마다 따로 투표하고, DAY2 헤드라이너도 페스티벌 시작일에 마감된다")
    void saveVote_WhenFestivalHeadliners_VotesSeparatelyAndClosesAtFestivalStart() {
        // given
        Concert festival = saveFestival();
        Artist ado = artistRepository.save(Artist.builder().name("Ado").build());
        Song adoSong = song(ado, "うっせぇわ", 1);
        Performance day1 = saveLineup(festival, yuuri, 1, true);
        Performance day2 = saveLineup(festival, ado, 2, true);

        // when
        service.saveVote(festival.getId(), day1.getId(), me.getId(), List.of(s1.getId()), BEFORE_DEADLINE);
        service.saveVote(festival.getId(), day2.getId(), me.getId(), List.of(adoSong.getId()), BEFORE_DEADLINE);

        // then
        assertThat(service.myVote(festival.getId(), day1.getId(), me.getId()).songIds()).containsExactly(s1.getId());
        assertThat(service.myVote(festival.getId(), day2.getId(), me.getId()).songIds()).containsExactly(adoSong.getId());
        assertErrorCode(() -> service.saveVote(festival.getId(), day2.getId(), me.getId(),
                List.of(adoSong.getId()), DEADLINE), ErrorCode.SETLIST_VOTE_CLOSED);
    }

    // ---------- 순위 ----------

    @Test
    @DisplayName("순위는 득표순이고 동점은 정렬 순번 순이며 상위 n곡을 하이라이트한다")
    void ranking_WithVotes_OrdersByVotesThenSortOrderAndHighlightsTopN() {
        // given
        voteAs(user("a"), s3.getId(), s2.getId());
        voteAs(user("b"), s3.getId(), s1.getId());
        voteAs(me, s2.getId());
        em.flush();
        em.clear();

        // when
        SetlistRankingResponse res = service.ranking(concert.getId(), performance.getId(), me.getId(), BEFORE_DEADLINE);

        // then - s2·s3 2표 동점 → 정렬 순번(2 < 3)으로 s2가 1위. n = 2라 s1은 하이라이트 아님
        assertThat(res.highlightCount()).isEqualTo(2);
        assertThat(res.participantCount()).isEqualTo(3);
        assertThat(res.votingClosed()).isFalse();
        assertThat(res.songs()).extracting(RankedSong::rank, RankedSong::titleOriginal, RankedSong::votes,
                        RankedSong::highlighted, RankedSong::mine)
                .containsExactly(
                        tuple(1, "ベテルギウス", 2L, true, true),
                        tuple(2, "いかないで", 2L, true, false),
                        tuple(3, "ドライフラワー", 1L, false, false));
        // 상세 화면 미리보기: 앞 곡만 자르고 나머지 값은 그대로. 곡 수보다 크게 잘라도 안전
        assertThat(res.top(2).songs()).extracting(RankedSong::rank).containsExactly(1, 2);
        assertThat(res.top(2).participantCount()).isEqualTo(3);
        assertThat(res.top(5).songs()).hasSize(3);
    }

    @Test
    @DisplayName("비로그인이면 mine이 모두 false이고 마감 후에도 순위는 그대로 보인다")
    void ranking_WhenAnonymousAfterDeadline_ShowsRankingWithoutMine() {
        // given
        voteAs(me, s1.getId());
        em.flush();
        em.clear();

        // when
        SetlistRankingResponse res = service.ranking(concert.getId(), performance.getId(), null, DEADLINE);

        // then
        assertThat(res.votingClosed()).isTrue();
        assertThat(res.songs()).singleElement().satisfies(r -> {
            assertThat(r.titleOriginal()).isEqualTo("ドライフラワー");
            assertThat(r.mine()).isFalse();
        });
    }

    @Test
    @DisplayName("투표가 0건이면 빈 순위이고, 예상 곡 수가 없으면 n은 기본값 20이다")
    void ranking_WhenNoVotesOrNoExpectedCount_ReturnsEmptyWithDefaultN() {
        // given
        Concert noCount = saveSoloConcert(true);
        Performance noCountPerformance = saveSolo(noCount, null);

        // when
        SetlistRankingResponse res = service.ranking(concert.getId(), performance.getId(), me.getId(), BEFORE_DEADLINE);

        // then
        assertThat(res.songs()).isEmpty();
        assertThat(res.participantCount()).isZero();
        assertThat(service.ranking(noCount.getId(), noCountPerformance.getId(), me.getId(), BEFORE_DEADLINE)
                .highlightCount()).isEqualTo(20);
    }

    @Test
    @DisplayName("페스티벌 비헤드라이너 순위는 셋리스트 미운영 응답이다")
    void ranking_WhenFestivalNonHeadliner_ReturnsNone() {
        // given
        Concert festival = saveFestival();
        Performance nonHeadliner = saveLineup(festival, yuuri, 1, false);

        // when
        SetlistRankingResponse res = service.ranking(festival.getId(), nonHeadliner.getId(), null, BEFORE_DEADLINE);

        // then
        assertThat(res.highlightCount()).isNull();
        assertThat(res.songs()).isEmpty();
    }

    @Test
    @DisplayName("비공개 공연의 순위를 조회하면 403")
    void ranking_WhenConcertHidden_ThrowsConcertNotOpen() {
        // given
        Concert hidden = saveSoloConcert(false);
        Performance hiddenPerformance = saveSolo(hidden, 2);

        // when & then
        assertErrorCode(() -> service.ranking(hidden.getId(), hiddenPerformance.getId(), null, BEFORE_DEADLINE),
                ErrorCode.CONCERT_NOT_OPEN);
    }

    // ---------- 결과 메일 수신 동의 ----------

    @Test
    @DisplayName("결과 메일 수신 동의는 투표에 저장되고 투표를 고쳐도 유지된다")
    void agreeResultMail_WhenVoted_PersistsConsentAcrossVoteUpdates() {
        // given
        vote(BEFORE_DEADLINE, s1.getId());

        // when
        MySetlistVoteResponse agreed = service.agreeResultMail(concert.getId(), performance.getId(), me.getId());
        em.flush();
        em.clear();
        vote(BEFORE_DEADLINE, s2.getId());

        // then
        assertThat(agreed.resultMailConsent()).isTrue();
        assertThat(service.myVote(concert.getId(), performance.getId(), me.getId()).resultMailConsent()).isTrue();
    }

    @Test
    @DisplayName("투표하지 않았으면 결과 메일 수신 동의는 404")
    void agreeResultMail_WhenNotVoted_ThrowsVoteNotFound() {
        // when & then
        assertErrorCode(() -> service.agreeResultMail(concert.getId(), performance.getId(), me.getId()),
                ErrorCode.SETLIST_VOTE_NOT_FOUND);
    }

    @Test
    @DisplayName("투표하지 않았으면 내 투표는 빈 응답이다")
    void myVote_WhenNotVoted_ReturnsEmpty() {
        // when
        MySetlistVoteResponse res = service.myVote(concert.getId(), performance.getId(), me.getId());

        // then
        assertThat(res.voted()).isFalse();
        assertThat(res.songIds()).isEmpty();
    }
}
