package com.loop.loop_backend.Performance.domain;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Performance.repository.PerformanceRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 출연 정보(라인업, AD-04) 저장 규칙:
// - 페스티벌 라인업은 페스티벌에만, DAY는 1~공연 일수.
// - 공연당 아티스트 1건. 여러 DAY 출연은 출연 DAY 목록(performance_days)으로 둔다.
// - 목록은 노출 순서대로, 출연 DAY도 함께 읽힌다.
// - 공연이나 아티스트가 지워지면 출연 정보와 출연 DAY도 지워진다.
@DataJpaTest
class PerformancePersistenceTest {

    @Autowired private PerformanceRepository performanceRepository;
    @Autowired private ConcertRepository concertRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private EntityManager em;

    private Concert festival;   // 3일 페스티벌
    private Artist yoasobi;
    private Artist yuuri;

    @BeforeEach
    void setUp() {
        festival = concertRepository.save(Concert.builder()
                .title("SUMMER SONIC")
                .category(ConcertCategory.FESTIVAL)
                .startDate(LocalDate.of(2026, 11, 20))
                .endDate(LocalDate.of(2026, 11, 22))
                .build());
        yoasobi = artistRepository.save(Artist.builder().name("YOASOBI").build());
        yuuri = artistRepository.save(Artist.builder().name("Yuuri").build());
    }

    private Performance add(Artist artist, int day) {
        return performanceRepository.save(Performance.builder()
                .concert(festival).artist(artist).day(day)
                .displayOrder(performanceRepository.findMaxDisplayOrder(festival.getId()) + 1)
                .build());
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("페스티벌이 아니면 라인업 항목을 만들 수 없다")
    void build_WhenNotFestival_ThrowsInvalidInput() {
        // given
        Concert solo = concertRepository.save(Concert.builder()
                .title("YUURI LIVE").category(ConcertCategory.STAGE)
                .startDate(LocalDate.of(2026, 11, 20)).endDate(LocalDate.of(2026, 11, 20))
                .build());

        // when & then
        assertThatThrownBy(() -> Performance.builder().concert(solo).artist(yuuri).day(1).displayOrder(1).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DAY는 1부터 공연 일수까지다")
    void build_WithDayOutOfRange_ThrowsInvalidInput() {
        // when & then
        assertThatThrownBy(() -> add(yuuri, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> add(yuuri, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThat(add(yuuri, 3).getDays()).containsExactly(3);
    }

    @Test
    @DisplayName("공연 기간이 미정이면 DAY를 정할 수 없다")
    void build_WhenPeriodUndecided_ThrowsInvalidInput() {
        // given
        Concert undated = concertRepository.save(Concert.builder()
                .title("미정 페스티벌").category(ConcertCategory.FESTIVAL).build());

        // when & then
        assertThatThrownBy(() -> Performance.builder().concert(undated).artist(yuuri).day(1).displayOrder(1).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("같은 공연에 같은 아티스트는 한 건만 저장된다")
    void save_WhenSameConcertAndArtist_ViolatesUniqueConstraint() {
        // given
        add(yuuri, 1);
        flushAndClear();

        // when & then
        assertThatThrownBy(() -> {
            add(yuuri, 2);
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("여러 DAY 출연은 출연 DAY 목록에 오름차순으로 저장된다")
    void addDay_StoresDaysSortedAndReadsBack() {
        // given
        Performance performance = add(yuuri, 3);

        // when
        performance.addDay(1);
        flushAndClear();

        // then
        Performance loaded = performanceRepository.findByConcertIdAndArtistId(festival.getId(), yuuri.getId()).orElseThrow();
        assertThat(loaded.getDays()).containsExactly(1, 3);
        assertThat(loaded.hasDay(3)).isTrue();
        assertThat(loaded.hasDay(2)).isFalse();
    }

    @Test
    @DisplayName("출연 DAY 목록을 통째로 바꾸고, 빈 목록이나 범위 밖 DAY는 거절한다")
    void replaceDays_ReplacesWholeListAndRejectsInvalid() {
        // given
        Performance performance = add(yuuri, 1);

        // when
        performance.replaceDays(List.of(3, 2));
        flushAndClear();

        // then
        assertThat(performanceRepository.findById(performance.getId()).orElseThrow().getDays()).containsExactly(2, 3);
        Performance loaded = performanceRepository.findById(performance.getId()).orElseThrow();
        assertThatThrownBy(() -> loaded.replaceDays(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> loaded.replaceDays(List.of(1, 4))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("기간 축소 정리는 범위를 넘는 DAY만 빼고 DAY가 다 빠졌는지 알려준다")
    void dropDaysAfter_RemovesOutOfRangeDaysAndReportsEmpty() {
        // given
        Performance both = add(yuuri, 1);
        both.addDay(3);
        Performance lastOnly = add(yoasobi, 3);

        // when & then
        assertThat(both.dropDaysAfter(2)).isFalse();
        assertThat(both.getDays()).containsExactly(1);
        assertThat(lastOnly.dropDaysAfter(2)).isTrue();
    }

    @Test
    @DisplayName("목록은 노출 순서대로 출연 DAY와 함께 나오고 새 항목은 맨 뒤다")
    void findByConcertIdOrderByDisplayOrderAsc_ReturnsInDisplayOrderWithDays() {
        // given
        Performance first = add(yoasobi, 2);
        Performance second = add(yuuri, 1);
        second.addDay(3);
        first.swapOrderWith(second);
        flushAndClear();

        // when
        List<Performance> list = performanceRepository.findByConcertIdOrderByDisplayOrderAsc(festival.getId());

        // then
        assertThat(list).extracting(l -> l.getArtist().getName()).containsExactly("Yuuri", "YOASOBI");
        assertThat(list.get(0).getDays()).containsExactly(1, 3);
        assertThat(performanceRepository.findMaxDisplayOrder(festival.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("동행 무대 검증 쿼리는 이 공연 소속이면서 그 DAY에 출연하는 항목만 센다")
    void countByIdInAndConcertIdAndDay_CountsOnlyPerformancesOnThatDay() {
        // given
        Performance yuuriDays = add(yuuri, 1);
        yuuriDays.addDay(2);
        Performance yoasobiDay3 = add(yoasobi, 3);
        flushAndClear();

        // when & then
        List<Long> ids = List.of(yuuriDays.getId(), yoasobiDay3.getId());
        assertThat(performanceRepository.countByIdInAndConcertIdAndDay(ids, festival.getId(), 2)).isEqualTo(1);
        assertThat(performanceRepository.countByIdInAndConcertIdAndDay(ids, festival.getId(), 3)).isEqualTo(1);
        assertThat(performanceRepository.countByIdInAndConcertIdAndDay(List.of(yuuriDays.getId()), festival.getId(), 1))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("공연이나 아티스트가 지워지면 출연 정보와 출연 DAY도 지워진다")
    void deleteConcertOrArtist_CascadesToPerformancesAndDays() {
        // given
        add(yoasobi, 1);
        add(yuuri, 1).addDay(2);
        flushAndClear();

        // when
        em.createNativeQuery("DELETE FROM artists WHERE id = :id").setParameter("id", yuuri.getId()).executeUpdate();

        // then
        assertThat(performanceRepository.findByConcertIdOrderByDisplayOrderAsc(festival.getId())).hasSize(1);
        em.createNativeQuery("DELETE FROM concerts WHERE id = :id").setParameter("id", festival.getId()).executeUpdate();
        assertThat(performanceRepository.count()).isZero();
        //noinspection SqlResolve
        assertThat(em.createNativeQuery("SELECT COUNT(*) FROM performance_days").getSingleResult()).isEqualTo(0L);
    }
}
