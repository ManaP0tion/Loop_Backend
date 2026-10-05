package com.loop.loop_backend.Lineup.domain;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Lineup.repository.LineupRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 라인업(AD-04) 저장 규칙:
// - 페스티벌에만, DAY는 1~공연 일수. 같은 아티스트는 DAY가 다르면 여러 번 등록된다.
// - 목록은 노출 순서대로, 기간 축소·유형 변경 정리용 삭제가 동작한다.
// - 공연이나 아티스트가 지워지면 라인업도 지워진다.
@DataJpaTest
class LineupPersistenceTest {

    @Autowired private LineupRepository lineupRepository;
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
                .category(ConcertCategory.JAPAN_FESTIVAL)
                .startDate(LocalDate.of(2026, 11, 20))
                .endDate(LocalDate.of(2026, 11, 22))
                .build());
        yoasobi = artistRepository.save(Artist.builder().name("YOASOBI").build());
        yuuri = artistRepository.save(Artist.builder().name("Yuuri").build());
    }

    private Lineup add(Artist artist, int day) {
        return lineupRepository.save(Lineup.builder()
                .concert(festival).artist(artist).day(day)
                .displayOrder(lineupRepository.findMaxDisplayOrder(festival.getId()) + 1)
                .build());
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    @Test
    void 페스티벌이_아니면_등록할_수_없다() {
        Concert solo = concertRepository.save(Concert.builder()
                .title("YUURI LIVE").category(ConcertCategory.J_POP_ARTIST)
                .startDate(LocalDate.of(2026, 11, 20)).endDate(LocalDate.of(2026, 11, 20))
                .build());

        assertThatThrownBy(() -> Lineup.builder().concert(solo).artist(yuuri).day(1).displayOrder(1).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void DAY는_1부터_공연_일수까지다() {
        assertThatThrownBy(() -> add(yuuri, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> add(yuuri, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThat(add(yuuri, 3).getDay()).isEqualTo(3);
    }

    @Test
    void 기간이_미정이면_DAY를_정할_수_없다() {
        Concert undated = concertRepository.save(Concert.builder()
                .title("미정 페스티벌").category(ConcertCategory.JAPAN_FESTIVAL).build());

        assertThatThrownBy(() -> Lineup.builder().concert(undated).artist(yuuri).day(1).displayOrder(1).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 같은_아티스트는_다른_DAY에_또_등록되지만_같은_DAY는_중복될_수_없다() {
        add(yuuri, 1);
        add(yuuri, 2);
        flushAndClear();

        assertThat(lineupRepository.existsByConcertIdAndArtistIdAndDay(festival.getId(), yuuri.getId(), 1)).isTrue();
        assertThatThrownBy(() -> {
            add(yuuri, 1);
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 목록은_노출_순서대로_나오고_새_항목은_맨_뒤다() {
        Lineup first = add(yoasobi, 2);
        Lineup second = add(yuuri, 1);
        first.swapOrderWith(second);
        flushAndClear();

        List<Lineup> list = lineupRepository.findByConcertIdOrderByDisplayOrderAsc(festival.getId());
        assertThat(list).extracting(l -> l.getArtist().getName()).containsExactly("Yuuri", "YOASOBI");
        assertThat(lineupRepository.findMaxDisplayOrder(festival.getId())).isEqualTo(2);
    }

    @Test
    void 기간_축소_정리는_범위를_넘는_DAY만_지운다() {
        add(yoasobi, 1);
        add(yuuri, 3);

        lineupRepository.deleteByConcertIdAndDayGreaterThan(festival.getId(), 2);

        assertThat(lineupRepository.findByConcertIdOrderByDisplayOrderAsc(festival.getId()))
                .extracting(Lineup::getDay).containsExactly(1);
    }

    @Test
    void 공연이나_아티스트가_지워지면_라인업도_지워진다() {
        add(yoasobi, 1);
        add(yuuri, 1);
        flushAndClear();

        em.createNativeQuery("DELETE FROM artists WHERE id = :id").setParameter("id", yuuri.getId()).executeUpdate();
        assertThat(lineupRepository.findByConcertIdOrderByDisplayOrderAsc(festival.getId())).hasSize(1);

        em.createNativeQuery("DELETE FROM concerts WHERE id = :id").setParameter("id", festival.getId()).executeUpdate();
        assertThat(lineupRepository.count()).isZero();
    }
}
