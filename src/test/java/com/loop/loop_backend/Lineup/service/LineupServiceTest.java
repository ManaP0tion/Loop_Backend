package com.loop.loop_backend.Lineup.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Lineup.dto.LineupAddRequest;
import com.loop.loop_backend.Lineup.dto.LineupItemResponse;
import com.loop.loop_backend.Lineup.dto.LineupPatchRequests.Direction;
import com.loop.loop_backend.Lineup.dto.LineupResponse;
import com.loop.loop_backend.Performance.domain.Performance;
import com.loop.loop_backend.Performance.repository.PerformanceRepository;
import com.loop.loop_backend.Setlist.domain.Setlist;
import com.loop.loop_backend.Setlist.domain.SetlistType;
import com.loop.loop_backend.Setlist.repository.SetlistRepository;
import com.loop.loop_backend.Setlist.repository.SetlistVoteRepository;
import com.loop.loop_backend.Setlist.service.SetlistDataGuard;
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
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

// 라인업 관리(AD-04, BE-13) 요구사항:
// - DB 선택(여러 명) 또는 직접 입력(아티스트 DB에도 저장) 중 하나로 추가하고, 새 항목은 맨 뒤에 붙는다.
// - 공연당 아티스트 1항목. 같은 아티스트를 다른 DAY로 추가하면 그 항목의 출연 DAY가 늘고(셋리스트·투표는 아티스트당 하나),
//   같은 DAY 중복은 409이고 아무것도 추가되지 않는다. 출연 DAY 목록은 통째로 바꾼다.
// - 순서는 한 칸씩 이동(맨 앞 UP·맨 뒤 DOWN은 변화 없음), 헤드라이너는 페스티벌당 최대 3팀(4팀째 지정은 400, 이미 헤드라이너인 항목 재지정은 통과).
// - 다른 공연의 라인업 항목은 404. 응답은 항상 그 공연의 라인업 전체.
// - 셋리스트·투표가 있는 항목은 삭제·헤드라이너 해제 409. 예상 곡 수는 헤드라이너만.
// - 동행 무대 검증: 고른 항목이 모두 그 공연 소속이고 그 DAY에 출연해야 하고, 미선택은 통과.
// - 유저용 조회(BE-14): DAY 목록 + 노출 순서대로 전체. 비공개 공연 403, 라인업 없음·페스티벌 아님은 빈 목록.
@DataJpaTest
class LineupServiceTest {

    @Autowired private PerformanceRepository performanceRepository;
    @Autowired private SetlistRepository setlistRepository;
    @Autowired private SetlistVoteRepository setlistVoteRepository;
    @Autowired private ConcertRepository concertRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private EntityManager em;

    private LineupService service;
    private Concert festival;   // 3일
    private Artist yoasobi;
    private Artist yuuri;
    private Artist vaundy;

    @BeforeEach
    void setUp() {
        service = new LineupService(performanceRepository, concertRepository, artistRepository,
                new SetlistDataGuard(setlistRepository, setlistVoteRepository));
        festival = saveFestival();
        yoasobi = artistRepository.save(Artist.builder().name("YOASOBI").build());
        yuuri = artistRepository.save(Artist.builder().name("Yuuri").build());
        vaundy = artistRepository.save(Artist.builder().name("Vaundy").build());
    }

    private Concert saveFestival() {
        return concertRepository.save(Concert.builder()
                .title("SUMMER SONIC").category(ConcertCategory.JAPAN_FESTIVAL)
                .startDate(LocalDate.of(2026, 11, 20)).endDate(LocalDate.of(2026, 11, 22))
                .build());
    }

    private List<LineupItemResponse> select(int day, Artist... artists) {
        List<Long> ids = Arrays.stream(artists).map(Artist::getId).toList();
        return service.add(festival.getId(), new LineupAddRequest(day, ids, null, null, null));
    }

    private static void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private Long idOf(List<LineupItemResponse> list, String name) {
        return list.stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow().lineupId();
    }

    private void publish(Concert concert) {
        concertRepository.findById(concert.getId()).orElseThrow().changePublished(true, LocalDateTime.now());
    }

    /** 라인업 항목에 실제 셋리스트를 붙인다. */
    private void attachSetlist(Long lineupId) {
        Performance performance = em.find(Performance.class, lineupId);
        Song song = Song.builder().artist(performance.getArtist()).titleOriginal("曲").sortOrder(1).build();
        em.persist(song);
        em.persist(Setlist.builder().performance(performance).type(SetlistType.ACTUAL).songs(List.of(song)).build());
        em.flush();
    }

    // ---------- 추가 ----------

    @Test
    @DisplayName("DB에서 여러 명을 고르면 요청 순서대로 맨 뒤에 붙는다")
    void add_WithSelectedArtists_AppendsInRequestOrder() {
        // given
        select(1, yoasobi);

        // when
        List<LineupItemResponse> list = select(2, vaundy, yuuri);

        // then
        assertThat(list).extracting(LineupItemResponse::name, LineupItemResponse::days, LineupItemResponse::displayOrder)
                .containsExactly(tuple("YOASOBI", List.of(1), 1), tuple("Vaundy", List.of(2), 2), tuple("Yuuri", List.of(2), 3));
    }

    @Test
    @DisplayName("직접 입력하면 아티스트 DB에도 저장된다")
    void add_WithDirectInput_SavesArtistToo() {
        // when
        List<LineupItemResponse> list = service.add(festival.getId(),
                new LineupAddRequest(1, null, "  Ado ", "https://img/ado.png", ConcertCategory.DOMESTIC_ARTIST));

        // then
        assertThat(list).singleElement().satisfies(r -> {
            assertThat(r.name()).isEqualTo("Ado");
            assertThat(r.imageUrl()).isEqualTo("https://img/ado.png");
            assertThat(r.category()).isEqualTo(ConcertCategory.DOMESTIC_ARTIST);
        });
        assertThat(artistRepository.findAll()).extracting(Artist::getName).contains("Ado");
    }

    @Test
    @DisplayName("artistIds와 name을 둘 다 보내거나 둘 다 안 보내면 400")
    void add_WithBothOrNeitherInput_ThrowsInvalidInput() {
        // when & then
        assertThatThrownBy(() -> service.add(festival.getId(),
                new LineupAddRequest(1, List.of(yuuri.getId()), "Ado", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.add(festival.getId(), new LineupAddRequest(1, List.of(), " ", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("없는 아티스트를 고르면 404")
    void add_WithUnknownArtist_ThrowsArtistNotFound() {
        // when & then
        assertErrorCode(() -> service.add(festival.getId(), new LineupAddRequest(1, List.of(9999L), null, null, null)),
                ErrorCode.ARTIST_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 있는 아티스트를 다른 DAY로 추가하면 새 항목 없이 출연 DAY가 늘고 순서는 그대로다")
    void add_WhenArtistAlreadyInLineup_AddsDayToSameItem() {
        // given
        select(1, yuuri);
        select(1, vaundy);

        // when
        List<LineupItemResponse> list = select(3, yuuri);

        // then
        assertThat(list).extracting(LineupItemResponse::name, LineupItemResponse::days, LineupItemResponse::displayOrder)
                .containsExactly(tuple("Yuuri", List.of(1, 3), 1), tuple("Vaundy", List.of(1), 2));
        assertThat(performanceRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 DAY에 이미 있는 아티스트가 섞이면 409이고 아무것도 추가되지 않는다")
    void add_WhenSameDayDuplicate_ThrowsConflictAndAddsNothing() {
        // given
        select(1, yuuri);

        // when & then
        assertErrorCode(() -> select(1, vaundy, yuuri), ErrorCode.DUPLICATE_LINEUP);
        em.clear();
        assertThat(service.list(festival.getId())).extracting(LineupItemResponse::name, LineupItemResponse::days)
                .containsExactly(tuple("Yuuri", List.of(1)));
    }

    @Test
    @DisplayName("DAY 범위 밖이거나 페스티벌이 아니면 400")
    void add_WithDayOutOfRangeOrNonFestival_ThrowsInvalidInput() {
        // given
        Concert solo = concertRepository.save(Concert.builder()
                .title("YUURI LIVE").category(ConcertCategory.J_POP_ARTIST)
                .startDate(LocalDate.of(2026, 11, 20)).endDate(LocalDate.of(2026, 11, 20)).build());

        // when & then
        assertThatThrownBy(() -> select(4, yuuri)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.add(solo.getId(), new LineupAddRequest(1, List.of(yuuri.getId()), null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("없는 공연의 라인업을 조회하면 404")
    void list_WhenConcertNotFound_ThrowsConcertNotFound() {
        // when & then
        assertErrorCode(() -> service.list(9999L), ErrorCode.CONCERT_NOT_FOUND);
    }

    // ---------- 출연 DAY 변경 · 삭제 ----------

    @Test
    @DisplayName("출연 DAY 목록을 통째로 바꾸고 빈 목록이나 범위 밖 DAY는 400")
    void replaceDays_ReplacesWholeListAndRejectsInvalid() {
        // given
        Long id = idOf(select(1, yuuri), "Yuuri");

        // when
        List<LineupItemResponse> list = service.replaceDays(festival.getId(), id, List.of(3, 2));

        // then
        assertThat(list).singleElement().satisfies(r -> assertThat(r.days()).containsExactly(2, 3));
        assertThatThrownBy(() -> service.replaceDays(festival.getId(), id, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.replaceDays(festival.getId(), id, List.of(4)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("출연 DAY를 바꿔도 셋리스트는 그대로 남는다")
    void replaceDays_KeepsSetlist() {
        // given
        Long id = idOf(select(1, yuuri), "Yuuri");
        service.changeHeadliner(festival.getId(), id, true);
        attachSetlist(id);

        // when
        service.replaceDays(festival.getId(), id, List.of(2, 3));

        // then
        assertThat(setlistRepository.findByPerformanceId(id)).hasSize(1);
    }

    @Test
    @DisplayName("삭제하면 목록에서 빠지고 아티스트는 남는다")
    void delete_RemovesItemButKeepsArtist() {
        // given
        List<LineupItemResponse> list = select(1, yuuri, vaundy);

        // when
        List<LineupItemResponse> after = service.delete(festival.getId(), idOf(list, "Yuuri"));

        // then
        assertThat(after).extracting(LineupItemResponse::name).containsExactly("Vaundy");
        assertThat(artistRepository.findById(yuuri.getId())).isPresent();
    }

    @Test
    @DisplayName("다른 공연의 라인업 항목은 404")
    void delete_WhenItemOfOtherConcert_ThrowsLineupNotFound() {
        // given
        Long lineupId = select(1, yuuri).get(0).lineupId();
        Concert other = saveFestival();

        // when & then
        assertErrorCode(() -> service.delete(other.getId(), lineupId), ErrorCode.LINEUP_NOT_FOUND);
        assertErrorCode(() -> service.changeHeadliner(other.getId(), lineupId, true), ErrorCode.LINEUP_NOT_FOUND);
        assertErrorCode(() -> service.replaceDays(other.getId(), lineupId, List.of(1)), ErrorCode.LINEUP_NOT_FOUND);
    }

    // ---------- 순서 · 헤드라이너 ----------

    @Test
    @DisplayName("한 칸씩 이동하고 맨 앞 UP과 맨 뒤 DOWN은 변화가 없다")
    void move_SwapsWithNeighborAndIgnoresEdges() {
        // given
        List<LineupItemResponse> list = select(1, yoasobi, yuuri, vaundy);

        // when & then
        assertThat(service.move(festival.getId(), idOf(list, "Vaundy"), Direction.UP))
                .extracting(LineupItemResponse::name).containsExactly("YOASOBI", "Vaundy", "Yuuri");
        assertThat(service.move(festival.getId(), idOf(list, "YOASOBI"), Direction.DOWN))
                .extracting(LineupItemResponse::name).containsExactly("Vaundy", "YOASOBI", "Yuuri");
        assertThat(service.move(festival.getId(), idOf(list, "Vaundy"), Direction.UP))
                .extracting(LineupItemResponse::name).containsExactly("Vaundy", "YOASOBI", "Yuuri");
        assertThat(service.move(festival.getId(), idOf(list, "Yuuri"), Direction.DOWN))
                .extracting(LineupItemResponse::name).containsExactly("Vaundy", "YOASOBI", "Yuuri");
    }

    @Test
    @DisplayName("헤드라이너를 지정하고 해제할 수 있다")
    void changeHeadliner_SetsAndUnsets() {
        // given
        List<LineupItemResponse> list = select(1, yoasobi, yuuri);

        // when
        service.changeHeadliner(festival.getId(), idOf(list, "YOASOBI"), true);
        List<LineupItemResponse> after = service.changeHeadliner(festival.getId(), idOf(list, "Yuuri"), true);

        // then
        assertThat(after).extracting(LineupItemResponse::headliner).containsExactly(true, true);
        assertThat(service.changeHeadliner(festival.getId(), idOf(list, "Yuuri"), false))
                .extracting(LineupItemResponse::headliner).containsExactly(true, false);
    }

    @Test
    @DisplayName("헤드라이너가 이미 3팀이면 4팀째 지정은 400이고, 기존 헤드라이너 재지정은 통과한다")
    void changeHeadliner_WhenAlreadyThree_ThrowsInvalidInputForFourth() {
        // given
        Artist aimer = artistRepository.save(Artist.builder().name("Aimer").build());
        List<LineupItemResponse> list = select(1, yoasobi, yuuri, vaundy, aimer);
        service.changeHeadliner(festival.getId(), idOf(list, "YOASOBI"), true);
        service.changeHeadliner(festival.getId(), idOf(list, "Yuuri"), true);
        service.changeHeadliner(festival.getId(), idOf(list, "Vaundy"), true);

        // when & then
        assertThatThrownBy(() -> service.changeHeadliner(festival.getId(), idOf(list, "Aimer"), true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.changeHeadliner(festival.getId(), idOf(list, "Vaundy"), true))
                .filteredOn(LineupItemResponse::headliner).hasSize(3);
    }

    // ---------- 헤드라이너 예상 셋리스트 ----------

    @Test
    @DisplayName("헤드라이너 예상 곡 수를 정하면 라인업 응답에 담긴다")
    void changeExpectedSongCount_WhenHeadliner_UpdatesCount() {
        // given
        Long id = idOf(select(1, yuuri), "Yuuri");
        service.changeHeadliner(festival.getId(), id, true);

        // when
        List<LineupItemResponse> list = service.changeExpectedSongCount(festival.getId(), id, 12);

        // then
        assertThat(list).singleElement().satisfies(r -> assertThat(r.expectedSongCount()).isEqualTo(12));
    }

    @Test
    @DisplayName("헤드라이너가 아닌 항목에 예상 곡 수를 정하면 400")
    void changeExpectedSongCount_WhenNotHeadliner_ThrowsInvalidInput() {
        // given
        Long id = idOf(select(1, yuuri), "Yuuri");

        // when & then
        assertThatThrownBy(() -> service.changeExpectedSongCount(festival.getId(), id, 12))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("셋리스트가 있는 헤드라이너는 해제할 수 없고, 지정은 그대로 된다")
    void changeHeadliner_WhenUnsettingWithSetlistData_ThrowsConflict() {
        // given
        Long id = idOf(select(1, yuuri), "Yuuri");
        service.changeHeadliner(festival.getId(), id, true);
        attachSetlist(id);

        // when & then
        assertErrorCode(() -> service.changeHeadliner(festival.getId(), id, false), ErrorCode.PERFORMANCE_HAS_SETLIST_DATA);
        assertThat(service.changeHeadliner(festival.getId(), id, true)).singleElement()
                .satisfies(r -> assertThat(r.headliner()).isTrue());
    }

    @Test
    @DisplayName("셋리스트가 있는 라인업 항목은 삭제할 수 없다")
    void delete_WhenSetlistDataExists_ThrowsConflict() {
        // given
        Long id = idOf(select(1, yuuri), "Yuuri");
        service.changeHeadliner(festival.getId(), id, true);
        attachSetlist(id);

        // when & then
        assertErrorCode(() -> service.delete(festival.getId(), id), ErrorCode.PERFORMANCE_HAS_SETLIST_DATA);
        assertThat(service.list(festival.getId())).hasSize(1);
    }

    // ---------- 유저용 조회 (BE-14) ----------

    @Test
    @DisplayName("유저용 조회는 DAY 목록과 노출 순서대로 전체를 준다")
    void publicLineup_ReturnsDaysAndItemsInDisplayOrder() {
        // given
        List<LineupItemResponse> list = select(2, yoasobi, yuuri);
        select(1, vaundy);
        service.changeHeadliner(festival.getId(), idOf(list, "YOASOBI"), true);
        publish(festival);

        // when
        LineupResponse res = service.publicLineup(festival.getId());

        // then
        assertThat(res.days()).extracting(LineupResponse.Day::day, LineupResponse.Day::date).containsExactly(
                tuple(1, LocalDate.of(2026, 11, 20)), tuple(2, LocalDate.of(2026, 11, 21)), tuple(3, LocalDate.of(2026, 11, 22)));
        assertThat(res.artists()).extracting(LineupItemResponse::name, LineupItemResponse::days, LineupItemResponse::headliner)
                .containsExactly(tuple("YOASOBI", List.of(2), true), tuple("Yuuri", List.of(2), false),
                        tuple("Vaundy", List.of(1), false));
    }

    @Test
    @DisplayName("여러 DAY에 나오는 헤드라이너는 항목 하나에 모든 출연 DAY와 헤드라이너 표시를 준다")
    void publicLineup_WhenHeadlinerOnSeveralDays_ReturnsOneItemWithAllDays() {
        // given - DAY1 = YOASOBI, DAY2·DAY3 = Yuuri 헤드라이너
        Long yoasobiId = idOf(select(1, yoasobi), "YOASOBI");
        Long yuuriId = idOf(select(2, yuuri), "Yuuri");
        select(3, yuuri);
        service.changeHeadliner(festival.getId(), yoasobiId, true);
        service.changeHeadliner(festival.getId(), yuuriId, true);
        publish(festival);

        // when
        LineupResponse res = service.publicLineup(festival.getId());

        // then
        assertThat(res.artists()).extracting(LineupItemResponse::name, LineupItemResponse::days, LineupItemResponse::headliner)
                .containsExactly(tuple("YOASOBI", List.of(1), true), tuple("Yuuri", List.of(2, 3), true));
    }

    @Test
    @DisplayName("라인업이 아직 없어도 DAY 목록과 빈 목록으로 응답한다")
    void publicLineup_WhenEmpty_ReturnsDaysAndEmptyList() {
        // given
        publish(festival);

        // when
        LineupResponse res = service.publicLineup(festival.getId());

        // then
        assertThat(res.days()).hasSize(3);
        assertThat(res.artists()).isEmpty();
    }

    @Test
    @DisplayName("비공개 공연의 라인업을 조회하면 403")
    void publicLineup_WhenConcertHidden_ThrowsConcertNotOpen() {
        // given
        select(1, yuuri);

        // when & then
        assertErrorCode(() -> service.publicLineup(festival.getId()), ErrorCode.CONCERT_NOT_OPEN);
    }

    @Test
    @DisplayName("페스티벌이 아니면 빈 라인업을 준다")
    void publicLineup_WhenNotFestival_ReturnsEmpty() {
        // given
        Concert solo = concertRepository.save(Concert.builder()
                .title("YUURI LIVE").category(ConcertCategory.J_POP_ARTIST)
                .startDate(LocalDate.of(2026, 11, 20)).endDate(LocalDate.of(2026, 11, 20)).build());
        publish(solo);

        // when
        LineupResponse res = service.publicLineup(solo.getId());

        // then
        assertThat(res.days()).isEmpty();
        assertThat(res.artists()).isEmpty();
    }

    // ---------- 동행 무대 검증 ----------

    @Test
    @DisplayName("그 공연·DAY에 출연하는 무대만 고르면 통과하고 미선택도 통과한다")
    void validateStages_WithStagesOnThatDay_Passes() {
        // given
        List<LineupItemResponse> list = select(1, yoasobi, yuuri);
        List<Long> ids = list.stream().map(LineupItemResponse::lineupId).toList();

        // when & then
        service.validateStages(festival.getId(), 1, ids);
        service.validateStages(festival.getId(), 1, List.of(ids.get(0), ids.get(0))); // 중복은 하나로 본다
        service.validateStages(festival.getId(), 1, List.of());
        service.validateStages(festival.getId(), 1, null);
    }

    @Test
    @DisplayName("여러 DAY에 나오는 무대는 출연한 DAY 어디서든 고를 수 있다")
    void validateStages_WhenStageOnSeveralDays_PassesForEachDay() {
        // given
        Long yuuriId = idOf(select(1, yuuri), "Yuuri");
        select(3, yuuri);

        // when & then
        service.validateStages(festival.getId(), 1, List.of(yuuriId));
        service.validateStages(festival.getId(), 3, List.of(yuuriId));
        assertThatThrownBy(() -> service.validateStages(festival.getId(), 2, List.of(yuuriId)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("다른 DAY·다른 공연·없는 무대가 섞이면 400")
    void validateStages_WithForeignStage_ThrowsInvalidInput() {
        // given
        Long day1 = select(1, yoasobi).get(0).lineupId();
        Long day2 = idOf(select(2, yuuri), "Yuuri");
        Concert other = saveFestival();
        Long otherFestival = service.add(other.getId(), new LineupAddRequest(1, List.of(vaundy.getId()), null, null, null))
                .get(0).lineupId();

        // when & then
        assertThatThrownBy(() -> service.validateStages(festival.getId(), 1, List.of(day1, day2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validateStages(festival.getId(), 1, List.of(day1, otherFestival)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validateStages(festival.getId(), 1, List.of(day1, 9999L)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
