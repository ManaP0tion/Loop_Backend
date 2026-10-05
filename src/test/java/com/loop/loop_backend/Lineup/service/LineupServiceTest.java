package com.loop.loop_backend.Lineup.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Lineup.dto.LineupItemResponse;
import com.loop.loop_backend.Lineup.dto.LineupAddRequest;
import com.loop.loop_backend.Lineup.dto.LineupPatchRequests.Direction;
import com.loop.loop_backend.Lineup.dto.LineupResponse;
import com.loop.loop_backend.Lineup.repository.LineupRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

// 라인업 관리(AD-04, BE-13) 요구사항:
// - DB 선택(여러 명) 또는 직접 입력(아티스트 DB에도 저장) 중 하나로 추가하고, 새 항목은 맨 뒤에 붙는다.
// - 같은 아티스트는 다른 DAY에 또 등록되지만 같은 DAY 중복은 409이고 아무것도 추가되지 않는다.
// - 순서는 한 칸씩 이동(맨 앞 UP·맨 뒤 DOWN은 변화 없음), 헤드라이너는 개수 제한 없이 지정.
// - 다른 공연의 라인업 항목은 404. 응답은 항상 그 공연의 라인업 전체.
// - 동행 무대 검증: 고른 항목이 모두 그 공연·DAY 라인업이어야 하고, 미선택은 통과.
// - 유저용 조회(BE-14): DAY 목록 + 노출 순서대로 전체. 비공개 공연 403, 라인업 없음·페스티벌 아님은 빈 목록.
@DataJpaTest
class LineupServiceTest {

    @Autowired private LineupRepository lineupRepository;
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
        service = new LineupService(lineupRepository, concertRepository, artistRepository);
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
        List<Long> ids = java.util.Arrays.stream(artists).map(Artist::getId).toList();
        return service.add(festival.getId(), new LineupAddRequest(day, ids, null, null));
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

    // ---------- 추가 ----------

    @Test
    void DB에서_여러_명을_고르면_요청_순서대로_맨_뒤에_붙는다() {
        select(1, yoasobi);
        List<LineupItemResponse> list = select(2, vaundy, yuuri);

        assertThat(list).extracting(LineupItemResponse::name, LineupItemResponse::day, LineupItemResponse::displayOrder)
                .containsExactly(tuple("YOASOBI", 1, 1), tuple("Vaundy", 2, 2), tuple("Yuuri", 2, 3));
    }

    @Test
    void 직접_입력하면_아티스트_DB에도_저장된다() {
        List<LineupItemResponse> list = service.add(festival.getId(),
                new LineupAddRequest(1, null, "  Ado ", "https://img/ado.png"));

        assertThat(list).singleElement().satisfies(r -> {
            assertThat(r.name()).isEqualTo("Ado");
            assertThat(r.imageUrl()).isEqualTo("https://img/ado.png");
        });
        assertThat(artistRepository.findAll()).extracting(Artist::getName).contains("Ado");
    }

    @Test
    void artistIds와_name을_둘_다_보내거나_둘_다_안_보내면_400() {
        assertThatThrownBy(() -> service.add(festival.getId(),
                new LineupAddRequest(1, List.of(yuuri.getId()), "Ado", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.add(festival.getId(), new LineupAddRequest(1, List.of(), " ", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 없는_아티스트를_고르면_404() {
        assertErrorCode(() -> service.add(festival.getId(), new LineupAddRequest(1, List.of(9999L), null, null)),
                ErrorCode.ARTIST_NOT_FOUND);
    }

    @Test
    void 같은_아티스트는_다른_DAY엔_되지만_같은_DAY는_409이고_하나도_추가되지_않는다() {
        select(1, yuuri);
        assertThat(select(2, yuuri)).hasSize(2);

        assertErrorCode(() -> select(1, vaundy, yuuri), ErrorCode.DUPLICATE_LINEUP);
        em.clear();
        assertThat(service.list(festival.getId())).extracting(LineupItemResponse::name)
                .containsExactly("Yuuri", "Yuuri");
    }

    @Test
    void DAY_범위_밖이나_페스티벌이_아니면_400() {
        assertThatThrownBy(() -> select(4, yuuri)).isInstanceOf(IllegalArgumentException.class);

        Concert solo = concertRepository.save(Concert.builder()
                .title("YUURI LIVE").category(ConcertCategory.J_POP_ARTIST)
                .startDate(LocalDate.of(2026, 11, 20)).endDate(LocalDate.of(2026, 11, 20)).build());
        assertThatThrownBy(() -> service.add(solo.getId(), new LineupAddRequest(1, List.of(yuuri.getId()), null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 없는_공연은_404() {
        assertErrorCode(() -> service.list(9999L), ErrorCode.CONCERT_NOT_FOUND);
    }

    // ---------- DAY 변경 · 삭제 ----------

    @Test
    void DAY를_바꿀_수_있고_옮길_DAY에_같은_아티스트가_있으면_409() {
        select(1, yuuri);
        List<LineupItemResponse> list = select(2, yuuri, vaundy);
        Long vaundyId = idOf(list, "Vaundy");
        Long yuuriDay1 = list.get(0).lineupId();

        assertThat(service.changeDay(festival.getId(), vaundyId, 3))
                .filteredOn(r -> r.name().equals("Vaundy")).extracting(LineupItemResponse::day).containsExactly(3);
        assertErrorCode(() -> service.changeDay(festival.getId(), yuuriDay1, 2), ErrorCode.DUPLICATE_LINEUP);
    }

    @Test
    void 삭제하면_목록에서_빠지고_아티스트는_남는다() {
        List<LineupItemResponse> list = select(1, yuuri, vaundy);

        List<LineupItemResponse> after = service.delete(festival.getId(), idOf(list, "Yuuri"));

        assertThat(after).extracting(LineupItemResponse::name).containsExactly("Vaundy");
        assertThat(artistRepository.findById(yuuri.getId())).isPresent();
    }

    @Test
    void 다른_공연의_라인업_항목은_404() {
        Long lineupId = select(1, yuuri).get(0).lineupId();
        Concert other = saveFestival();

        assertErrorCode(() -> service.delete(other.getId(), lineupId), ErrorCode.LINEUP_NOT_FOUND);
        assertErrorCode(() -> service.changeHeadliner(other.getId(), lineupId, true), ErrorCode.LINEUP_NOT_FOUND);
    }

    // ---------- 순서 · 헤드라이너 ----------

    @Test
    void 한_칸씩_이동하고_맨_앞_UP과_맨_뒤_DOWN은_변화_없다() {
        List<LineupItemResponse> list = select(1, yoasobi, yuuri, vaundy);

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
    void 헤드라이너는_개수_제한_없이_지정하고_해제할_수_있다() {
        List<LineupItemResponse> list = select(1, yoasobi, yuuri);
        service.changeHeadliner(festival.getId(), idOf(list, "YOASOBI"), true);
        List<LineupItemResponse> after = service.changeHeadliner(festival.getId(), idOf(list, "Yuuri"), true);
        assertThat(after).extracting(LineupItemResponse::headliner).containsExactly(true, true);

        assertThat(service.changeHeadliner(festival.getId(), idOf(list, "Yuuri"), false))
                .extracting(LineupItemResponse::headliner).containsExactly(true, false);
    }

    // ---------- 유저용 조회 (BE-14) ----------

    private void publish(Concert concert) {
        concertRepository.findById(concert.getId()).orElseThrow().changePublished(true, java.time.LocalDateTime.now());
    }

    @Test
    void 유저용_조회는_DAY_목록과_노출_순서대로_전체를_준다() {
        List<LineupItemResponse> list = select(2, yoasobi, yuuri);
        select(1, vaundy);
        service.changeHeadliner(festival.getId(), idOf(list, "YOASOBI"), true);
        publish(festival);

        LineupResponse res = service.publicLineup(festival.getId());

        assertThat(res.days()).extracting(LineupResponse.Day::day, LineupResponse.Day::date).containsExactly(
                tuple(1, LocalDate.of(2026, 11, 20)), tuple(2, LocalDate.of(2026, 11, 21)), tuple(3, LocalDate.of(2026, 11, 22)));
        assertThat(res.artists()).extracting(LineupItemResponse::name, LineupItemResponse::day, LineupItemResponse::headliner)
                .containsExactly(tuple("YOASOBI", 2, true), tuple("Yuuri", 2, false), tuple("Vaundy", 1, false));
    }

    @Test
    void 라인업이_아직_없어도_DAY_목록과_빈_목록으로_200() {
        publish(festival);

        LineupResponse res = service.publicLineup(festival.getId());

        assertThat(res.days()).hasSize(3);
        assertThat(res.artists()).isEmpty();
    }

    @Test
    void 비공개_공연은_403() {
        select(1, yuuri);
        assertErrorCode(() -> service.publicLineup(festival.getId()), ErrorCode.CONCERT_NOT_OPEN);
    }

    @Test
    void 페스티벌이_아니면_빈_응답() {
        Concert solo = concertRepository.save(Concert.builder()
                .title("YUURI LIVE").category(ConcertCategory.J_POP_ARTIST)
                .startDate(LocalDate.of(2026, 11, 20)).endDate(LocalDate.of(2026, 11, 20)).build());
        publish(solo);

        LineupResponse res = service.publicLineup(solo.getId());

        assertThat(res.days()).isEmpty();
        assertThat(res.artists()).isEmpty();
    }

    // ---------- 동행 무대 검증 ----------

    @Test
    void 같은_공연_DAY의_무대만_고르면_통과하고_미선택도_통과한다() {
        List<LineupItemResponse> list = select(1, yoasobi, yuuri);
        List<Long> ids = list.stream().map(LineupItemResponse::lineupId).toList();

        service.validateStages(festival.getId(), 1, ids);
        service.validateStages(festival.getId(), 1, List.of(ids.get(0), ids.get(0))); // 중복은 하나로 본다
        service.validateStages(festival.getId(), 1, List.of());
        service.validateStages(festival.getId(), 1, null);
    }

    @Test
    void 다른_DAY_다른_공연_없는_무대가_섞이면_400() {
        Long day1 = select(1, yoasobi).get(0).lineupId();
        Long day2 = idOf(select(2, yuuri), "Yuuri");
        Concert other = saveFestival();
        Long otherFestival = service.add(other.getId(), new LineupAddRequest(1, List.of(vaundy.getId()), null, null))
                .get(0).lineupId();

        assertThatThrownBy(() -> service.validateStages(festival.getId(), 1, List.of(day1, day2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validateStages(festival.getId(), 1, List.of(day1, otherFestival)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validateStages(festival.getId(), 1, List.of(day1, 9999L)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
