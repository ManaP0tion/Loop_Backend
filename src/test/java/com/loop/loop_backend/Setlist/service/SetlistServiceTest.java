package com.loop.loop_backend.Setlist.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Performance.domain.Performance;
import com.loop.loop_backend.Performance.repository.PerformanceRepository;
import com.loop.loop_backend.Setlist.domain.SetlistType;
import com.loop.loop_backend.Setlist.dto.PreviousActualSetlistResponse;
import com.loop.loop_backend.Setlist.dto.SetlistResponse;
import com.loop.loop_backend.Setlist.dto.SetlistSaveRequest;
import com.loop.loop_backend.Setlist.dto.SetlistSongResponse;
import com.loop.loop_backend.Setlist.event.SetlistResultSavedEvent;
import com.loop.loop_backend.Setlist.repository.SetlistRepository;
import com.loop.loop_backend.Song.Repository.SongRepository;
import com.loop.loop_backend.Song.domain.Song;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

// 셋리스트 관리(AD-07) 요구사항 - 출연 정보(Performance) 단위:
// - 지난 셋리스트(최근 공연·지난 내한)와 실제 셋리스트를 같은 방식으로 저장하고, 다시 저장하면 통째로 교체된다.
// - 곡은 출연 아티스트의 (삭제되지 않은) 곡만, 요청 순서·중복 그대로. 곡 수는 자동 계산.
// - 지난 셋리스트는 날짜·장소 필수. 단독 공연과 페스티벌 헤드라이너만 저장할 수 있다.
// - 목록은 최근 공연 → 지난 내한 → 실제 순. 응답은 항상 그 출연 정보의 셋리스트 전체.
@DataJpaTest
class SetlistServiceTest {

    @Autowired private SetlistRepository setlistRepository;
    @Autowired private ConcertRepository concertRepository;
    @Autowired private PerformanceRepository performanceRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private SongRepository songRepository;
    @Autowired private EntityManager em;

    private SetlistService service;
    private final List<Object> events = new ArrayList<>();
    private Artist yuuri;
    private Concert concert;
    private Performance performance;
    private Song s1, s2, s3;

    @BeforeEach
    void setUp() {
        service = new SetlistService(setlistRepository, performanceRepository, songRepository, events::add);
        yuuri = artistRepository.save(Artist.builder().name("Yuuri").build());
        concert = saveSoloConcert("YUURI LIVE", false);
        performance = saveSolo(concert);
        s1 = song(yuuri, "ドライフラワー", 1);
        s2 = song(yuuri, "ベテルギウス", 2);
        s3 = song(yuuri, "いかないで", 3);
    }

    private Concert saveSoloConcert(String title, boolean published) {
        return concertRepository.save(Concert.builder()
                .title(title).category(ConcertCategory.STAGE).artist(yuuri).published(published)
                .startDate(LocalDate.of(2026, 11, 20)).endDate(LocalDate.of(2026, 11, 20))
                .build());
    }

    private Performance saveSolo(Concert concert) {
        return performanceRepository.save(Performance.solo(concert, concert.getArtist()));
    }

    private Performance saveFestivalLineup(boolean headliner) {
        Concert festival = concertRepository.save(Concert.builder()
                .title("SUMMER SONIC").category(ConcertCategory.FESTIVAL)
                .startDate(LocalDate.of(2026, 11, 20)).endDate(LocalDate.of(2026, 11, 21))
                .build());
        Performance lineup = Performance.builder().concert(festival).artist(yuuri).day(1).displayOrder(1).build();
        lineup.changeHeadliner(headliner);
        return performanceRepository.save(lineup);
    }

    private Song song(Artist artist, String title, int sortOrder) {
        return songRepository.save(Song.builder().artist(artist).titleOriginal(title).sortOrder(sortOrder).build());
    }

    private List<SetlistResponse> save(SetlistType type, SetlistSaveRequest req) {
        return service.save(concert.getId(), performance.getId(), type, req);
    }

    private static SetlistSaveRequest past(Long... songIds) {
        return new SetlistSaveRequest("Arena Tour", LocalDate.of(2026, 5, 1), "Zepp Haneda", List.of(songIds));
    }

    private static SetlistSaveRequest actual(Long... songIds) {
        return new SetlistSaveRequest(null, null, null, List.of(songIds));
    }

    private static void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("지난 셋리스트는 헤더와 곡을 순서대로 저장하고 곡 수를 센다")
    void save_WithPastSetlist_StoresHeaderAndSongsInOrder() {
        // when
        List<SetlistResponse> list = save(SetlistType.RECENT, past(s2.getId(), s1.getId()));

        // then
        assertThat(list).singleElement().satisfies(r -> {
            assertThat(r.type()).isEqualTo(SetlistType.RECENT);
            assertThat(r.tourName()).isEqualTo("Arena Tour");
            assertThat(r.performedOn()).isEqualTo(LocalDate.of(2026, 5, 1));
            assertThat(r.venueName()).isEqualTo("Zepp Haneda");
            assertThat(r.songCount()).isEqualTo(2);
            assertThat(r.songs()).extracting(s -> s.position() + ":" + s.titleOriginal())
                    .containsExactly("1:ベテルギウス", "2:ドライフラワー");
        });
    }

    @Test
    @DisplayName("실제 셋리스트는 중복 곡을 그대로 담고 헤더는 비운다")
    void save_WithActualSetlist_KeepsDuplicatesAndClearsHeader() {
        // when
        List<SetlistResponse> list = save(SetlistType.ACTUAL,
                new SetlistSaveRequest("무시", LocalDate.of(2026, 1, 1), "무시", List.of(s1.getId(), s2.getId(), s1.getId())));

        // then
        SetlistResponse actual = list.get(0);
        assertThat(actual.tourName()).isNull();
        assertThat(actual.venueName()).isNull();
        assertThat(actual.songs()).extracting(s -> s.songId()).containsExactly(s1.getId(), s2.getId(), s1.getId());
    }

    @Test
    @DisplayName("같은 구분으로 다시 저장하면 통째로 교체된다")
    void save_WhenAlreadyExists_ReplacesWholeSetlist() {
        // given
        save(SetlistType.ACTUAL, actual(s1.getId(), s2.getId()));
        em.clear();

        // when
        List<SetlistResponse> list = save(SetlistType.ACTUAL, actual(s3.getId()));

        // then
        assertThat(list).singleElement().satisfies(r ->
                assertThat(r.songs()).extracting(s -> s.songId()).containsExactly(s3.getId()));
        assertThat(setlistRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("목록은 최근 공연, 지난 내한, 실제 순이다")
    void list_ReturnsRecentThenPreviousVisitThenActual() {
        // given
        save(SetlistType.ACTUAL, actual(s1.getId()));
        save(SetlistType.PREVIOUS_VISIT, past(s2.getId()));
        save(SetlistType.RECENT, past(s3.getId()));
        em.clear();

        // when & then
        assertThat(service.list(concert.getId(), performance.getId())).extracting(SetlistResponse::type)
                .containsExactly(SetlistType.RECENT, SetlistType.PREVIOUS_VISIT, SetlistType.ACTUAL);
    }

    @Test
    @DisplayName("출연 아티스트의 곡이 아니거나 없는 곡이면 400")
    void save_WithOtherArtistOrUnknownSong_ThrowsInvalidInput() {
        // given
        Artist other = artistRepository.save(Artist.builder().name("Ado").build());
        Song otherSong = song(other, "うっせぇわ", 1);

        // when & then
        assertThatThrownBy(() -> save(SetlistType.ACTUAL, actual(s1.getId(), otherSong.getId())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> save(SetlistType.ACTUAL, actual(9999L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(setlistRepository.count()).isZero();
    }

    @Test
    @DisplayName("삭제된 곡으로는 셋리스트를 저장할 수 없다")
    void save_WithDeletedSong_ThrowsInvalidInput() {
        // given
        songRepository.delete(s1);
        em.flush();
        em.clear();

        // when & then
        assertThatThrownBy(() -> save(SetlistType.ACTUAL, actual(s1.getId())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("저장 후 곡이 삭제돼도 셋리스트에는 순서대로 남고 삭제 표시가 붙는다")
    void list_WhenSongDeletedAfterSave_KeepsSongWithDeletedFlag() {
        // given
        save(SetlistType.ACTUAL, actual(s1.getId(), s2.getId(), s3.getId()));
        em.flush();
        em.clear();
        songRepository.delete(songRepository.findById(s2.getId()).orElseThrow());
        em.flush();
        em.clear();

        // when
        SetlistResponse actual = service.list(concert.getId(), performance.getId()).get(0);

        // then
        assertThat(actual.songCount()).isEqualTo(3);
        assertThat(actual.songs()).extracting(SetlistSongResponse::position, SetlistSongResponse::songId,
                        SetlistSongResponse::deleted)
                .containsExactly(tuple(1, s1.getId(), false), tuple(2, s2.getId(), true), tuple(3, s3.getId(), false));
    }

    @Test
    @DisplayName("지난 셋리스트에 날짜나 장소가 없으면 400")
    void save_WhenPastSetlistMissingDateOrVenue_ThrowsInvalidInput() {
        // when & then
        assertThatThrownBy(() -> save(SetlistType.RECENT,
                new SetlistSaveRequest(null, null, "Zepp", List.of(s1.getId()))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> save(SetlistType.PREVIOUS_VISIT,
                new SetlistSaveRequest(null, LocalDate.of(2026, 5, 1), " ", List.of(s1.getId()))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("예상 곡 수가 없는 출연 정보도 실제 셋리스트를 저장할 수 있다")
    void save_WhenExpectedSongCountMissing_StillSavesActual() {
        // when & then - setUp의 출연 정보는 예상 곡 수가 없다(기본값 사용)
        assertThat(save(SetlistType.ACTUAL, actual(s1.getId()))).hasSize(1);
        assertThat(save(SetlistType.RECENT, past(s1.getId()))).hasSize(2);
    }

    @Test
    @DisplayName("페스티벌 헤드라이너는 셋리스트를 저장할 수 있다")
    void save_WhenFestivalHeadliner_SavesSetlist() {
        // given
        Performance headliner = saveFestivalLineup(true);
        Long festivalId = headliner.getConcert().getId();

        // when
        List<SetlistResponse> list = service.save(festivalId, headliner.getId(), SetlistType.ACTUAL, actual(s1.getId()));

        // then
        assertThat(list).singleElement().satisfies(r -> assertThat(r.type()).isEqualTo(SetlistType.ACTUAL));
    }

    @Test
    @DisplayName("페스티벌 비헤드라이너에는 셋리스트를 저장할 수 없다")
    void save_WhenFestivalNonHeadliner_ThrowsInvalidInput() {
        // given
        Performance nonHeadliner = saveFestivalLineup(false);
        Long festivalId = nonHeadliner.getConcert().getId();

        // when & then
        assertThatThrownBy(() -> service.save(festivalId, nonHeadliner.getId(), SetlistType.RECENT, past(s1.getId())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(setlistRepository.count()).isZero();
    }

    @Test
    @DisplayName("지난 셋리스트를 삭제하면 목록에서 빠지고 곡은 남는다")
    void delete_WithPastSetlist_RemovesSetlistButKeepsSongs() {
        // given
        save(SetlistType.ACTUAL, actual(s1.getId()));
        save(SetlistType.RECENT, past(s2.getId()));

        // when
        List<SetlistResponse> list = service.delete(concert.getId(), performance.getId(), SetlistType.RECENT);

        // then
        assertThat(list).extracting(SetlistResponse::type).containsExactly(SetlistType.ACTUAL);
        assertThat(songRepository.findById(s2.getId())).isPresent();
    }

    @Test
    @DisplayName("유저용 지난 셋리스트는 실제 셋리스트를 빼고 최근 공연, 지난 내한 순이다")
    void pastSetlists_ExcludesActualAndOrdersRecentFirst() {
        // given
        Concert open = saveSoloConcert("OPEN", true);
        Performance openPerformance = saveSolo(open);
        service.save(open.getId(), openPerformance.getId(), SetlistType.ACTUAL, actual(s1.getId()));
        service.save(open.getId(), openPerformance.getId(), SetlistType.PREVIOUS_VISIT, past(s2.getId()));
        service.save(open.getId(), openPerformance.getId(), SetlistType.RECENT, past(s3.getId(), s1.getId()));
        em.clear();

        // when
        List<SetlistResponse> past = service.pastSetlists(open.getId(), openPerformance.getId());

        // then
        assertThat(past).extracting(SetlistResponse::type).containsExactly(SetlistType.RECENT, SetlistType.PREVIOUS_VISIT);
        assertThat(past.get(0).songCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("유저용 지난 셋리스트는 비공개 공연이면 403, 없으면 빈 목록이다")
    void pastSetlists_WhenHiddenOrEmpty_ThrowsOrReturnsEmpty() {
        // given
        Concert open = saveSoloConcert("OPEN", true);
        Performance openPerformance = saveSolo(open);

        // when & then
        assertErrorCode(() -> service.pastSetlists(concert.getId(), performance.getId()), ErrorCode.CONCERT_NOT_OPEN);
        assertThat(service.pastSetlists(open.getId(), openPerformance.getId())).isEmpty();
    }

    @Test
    @DisplayName("결과 메일 이벤트는 실제 셋리스트 최초 저장에만 공연·출연 정보와 함께 발행된다")
    void save_WhenActualSavedFirstTime_PublishesResultEventOnce() {
        // given
        save(SetlistType.RECENT, past(s1.getId()));
        assertThat(events).isEmpty();

        // when
        save(SetlistType.ACTUAL, actual(s1.getId()));
        save(SetlistType.ACTUAL, actual(s2.getId()));

        // then
        assertThat(events).containsExactly(
                new SetlistResultSavedEvent(concert.getId(), performance.getId(), "YUURI LIVE"));
    }

    @Test
    @DisplayName("실제 셋리스트는 결과 메일 재발송을 막으려고 삭제할 수 없다")
    void delete_WithActualSetlist_ThrowsInvalidInput() {
        // given
        save(SetlistType.ACTUAL, actual(s1.getId()));

        // when & then
        assertThatThrownBy(() -> service.delete(concert.getId(), performance.getId(), SetlistType.ACTUAL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(setlistRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("없는 셋리스트 삭제는 404, 없거나 다른 공연의 출연 정보는 404")
    void delete_WhenSetlistOrPerformanceNotFound_ThrowsNotFound() {
        // given
        Concert another = saveSoloConcert("OTHER", false);

        // when & then
        assertErrorCode(() -> service.delete(concert.getId(), performance.getId(), SetlistType.RECENT),
                ErrorCode.SETLIST_NOT_FOUND);
        assertErrorCode(() -> service.list(concert.getId(), 9999L), ErrorCode.PERFORMANCE_NOT_FOUND);
        assertErrorCode(() -> service.save(another.getId(), performance.getId(), SetlistType.ACTUAL, actual(s1.getId())),
                ErrorCode.PERFORMANCE_NOT_FOUND);
    }

    // ---------- 이전 실제 셋리스트 불러오기 (F-1) ----------

    private Concert saveSoloConcertOn(String title, LocalDate startDate) {
        return concertRepository.save(Concert.builder()
                .title(title).category(ConcertCategory.STAGE).artist(yuuri).venue("Zepp Haneda")
                .startDate(startDate).endDate(startDate)
                .build());
    }

    @Test
    @DisplayName("이전 실제 셋리스트는 같은 아티스트의 다른 공연 것만 공연일 최신순으로 헤더 제안값과 함께 준다")
    void previousActuals_ReturnsSameArtistOtherActualsNewestFirst() {
        // given - 지난 공연 2건의 실제 셋리스트, 지난 셋리스트(RECENT)·자기 자신·다른 아티스트는 빠져야 한다
        Concert older = saveSoloConcertOn("YUURI 2024", LocalDate.of(2024, 5, 1));
        Performance olderPerformance = saveSolo(older);
        Concert newer = saveSoloConcertOn("YUURI 2025", LocalDate.of(2025, 6, 1));
        Performance newerPerformance = saveSolo(newer);
        service.save(older.getId(), olderPerformance.getId(), SetlistType.ACTUAL, actual(s1.getId()));
        service.save(newer.getId(), newerPerformance.getId(), SetlistType.ACTUAL, actual(s2.getId(), s3.getId()));
        service.save(newer.getId(), newerPerformance.getId(), SetlistType.RECENT, past(s1.getId()));
        save(SetlistType.ACTUAL, actual(s3.getId()));
        Artist ado = artistRepository.save(Artist.builder().name("Ado").build());
        Concert adoConcert = concertRepository.save(Concert.builder().title("ADO").category(ConcertCategory.STAGE)
                .artist(ado).startDate(LocalDate.of(2025, 12, 1)).endDate(LocalDate.of(2025, 12, 1)).build());
        Performance adoPerformance = performanceRepository.save(Performance.solo(adoConcert, ado));
        service.save(adoConcert.getId(), adoPerformance.getId(), SetlistType.ACTUAL, actual(song(ado, "うっせぇわ", 1).getId()));
        em.flush();
        em.clear();

        // when
        List<PreviousActualSetlistResponse> res = service.previousActuals(concert.getId(), performance.getId());

        // then
        assertThat(res).extracting(PreviousActualSetlistResponse::concertId).containsExactly(newer.getId(), older.getId());
        PreviousActualSetlistResponse first = res.get(0);
        assertThat(first.tourName()).isEqualTo("YUURI 2025");
        assertThat(first.performedOn()).isEqualTo(LocalDate.of(2025, 6, 1));
        assertThat(first.venueName()).isEqualTo("Zepp Haneda");
        assertThat(first.songCount()).isEqualTo(2);
        assertThat(first.songs()).extracting(SetlistSongResponse::songId).containsExactly(s2.getId(), s3.getId());
    }

    @Test
    @DisplayName("페스티벌 헤드라이너의 실제 셋리스트도 나오고 날짜는 시작일 + (첫 출연 DAY - 1)이다")
    void previousActuals_IncludesFestivalHeadlinerWithDayDate() {
        // given - DAY2·DAY3 헤드라이너
        Concert festival = concertRepository.save(Concert.builder()
                .title("SUMMER SONIC").category(ConcertCategory.FESTIVAL)
                .startDate(LocalDate.of(2025, 8, 15)).endDate(LocalDate.of(2025, 8, 17))
                .build());
        Performance headliner = Performance.builder().concert(festival).artist(yuuri).day(2).displayOrder(1).build();
        headliner.addDay(3);
        headliner.changeHeadliner(true);
        performanceRepository.save(headliner);
        service.save(festival.getId(), headliner.getId(), SetlistType.ACTUAL, actual(s1.getId()));
        em.flush();
        em.clear();

        // when
        List<PreviousActualSetlistResponse> res = service.previousActuals(concert.getId(), performance.getId());

        // then
        assertThat(res).singleElement().satisfies(r -> {
            assertThat(r.tourName()).isEqualTo("SUMMER SONIC");
            assertThat(r.performedOn()).isEqualTo(LocalDate.of(2025, 8, 16));
        });
    }

    @Test
    @DisplayName("불러온 셋리스트의 삭제된 곡에는 삭제 표시가 붙는다")
    void previousActuals_MarksDeletedSongs() {
        // given
        Concert older = saveSoloConcertOn("YUURI 2024", LocalDate.of(2024, 5, 1));
        Performance olderPerformance = saveSolo(older);
        service.save(older.getId(), olderPerformance.getId(), SetlistType.ACTUAL, actual(s1.getId(), s2.getId()));
        em.flush();
        em.clear();
        songRepository.delete(songRepository.findById(s1.getId()).orElseThrow());
        em.flush();
        em.clear();

        // when
        List<PreviousActualSetlistResponse> res = service.previousActuals(concert.getId(), performance.getId());

        // then
        assertThat(res.get(0).songs()).extracting(SetlistSongResponse::songId, SetlistSongResponse::deleted)
                .containsExactly(tuple(s1.getId(), true), tuple(s2.getId(), false));
    }

    @Test
    @DisplayName("다른 공연의 출연 정보로 불러오면 404")
    void previousActuals_WhenPerformanceNotInConcert_ThrowsNotFound() {
        // given
        Concert another = saveSoloConcert("OTHER", false);

        // when & then
        assertErrorCode(() -> service.previousActuals(another.getId(), performance.getId()), ErrorCode.PERFORMANCE_NOT_FOUND);
    }
}
