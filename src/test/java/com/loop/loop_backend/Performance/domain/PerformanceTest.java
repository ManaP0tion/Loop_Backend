package com.loop.loop_backend.Performance.domain;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 출연 정보 규칙:
// - 단독 공연 출연 정보는 DAY 1·헤드라이너로 만들어지고 항상 셋리스트 대상이다. 페스티벌에는 만들 수 없다.
// - 페스티벌 라인업은 헤드라이너만 셋리스트 대상이다.
// - 예상 곡 수는 1 이상, null이면 비운다.
// - 예상 곡 수 기본값은 만들 때 저장한다: 내한(단독) 공연 25곡, 페스티벌은 헤드라이너로 지정할 때 비어 있으면 20곡.
class PerformanceTest {

    private static final Artist YUURI = Artist.builder().name("Yuuri").build();

    private static Concert solo() {
        return Concert.builder().title("YUURI LIVE").category(ConcertCategory.STAGE).artist(YUURI).build();
    }

    private static Concert festival() {
        return Concert.builder().title("SUMMER SONIC").category(ConcertCategory.FESTIVAL)
                .startDate(LocalDate.of(2026, 11, 20)).endDate(LocalDate.of(2026, 11, 21)).build();
    }

    @Test
    @DisplayName("단독 공연 출연 정보는 DAY 1 헤드라이너로 만들어지고 셋리스트 대상이다")
    void solo_WhenSoloConcert_CreatesDayOneHeadlinerAsSetlistTarget() {
        // when
        Performance performance = Performance.solo(solo(), YUURI);

        // then
        assertThat(performance.getDays()).containsExactly(1);
        assertThat(performance.isHeadliner()).isTrue();
        assertThat(performance.isSetlistTarget()).isTrue();
    }

    @Test
    @DisplayName("페스티벌에는 단독 공연 출연 정보를 만들 수 없다")
    void solo_WhenFestival_ThrowsInvalidInput() {
        // when & then
        assertThatThrownBy(() -> Performance.solo(festival(), YUURI)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("페스티벌 라인업은 헤드라이너일 때만 셋리스트 대상이다")
    void isSetlistTarget_WhenFestival_TrueOnlyForHeadliner() {
        // given
        Performance lineup = Performance.builder().concert(festival()).artist(YUURI).day(2).displayOrder(1).build();

        // when & then
        assertThat(lineup.isSetlistTarget()).isFalse();
        lineup.changeHeadliner(true);
        assertThat(lineup.isSetlistTarget()).isTrue();
    }

    @Test
    @DisplayName("예상 곡 수는 1 이상이고 null이면 비워진다")
    void changeExpectedSongCount_ValidatesMinimumAndAllowsNull() {
        // given
        Performance performance = Performance.solo(solo(), YUURI);

        // when & then
        assertThatThrownBy(() -> performance.changeExpectedSongCount(0)).isInstanceOf(IllegalArgumentException.class);
        performance.changeExpectedSongCount(15);
        assertThat(performance.getExpectedSongCount()).isEqualTo(15);
        performance.changeExpectedSongCount(null);
        assertThat(performance.getExpectedSongCount()).isNull();
    }

    @Test
    @DisplayName("내한 공연 출연 정보는 예상 곡 수 25곡으로 만들어진다")
    void solo_StartsWithExpectedSongCount25() {
        assertThat(Performance.solo(solo(), YUURI).getExpectedSongCount()).isEqualTo(25);
    }

    @Test
    @DisplayName("페스티벌 라인업은 헤드라이너로 지정할 때 예상 곡 수가 비어 있으면 20곡이 된다")
    void changeHeadliner_WhenEmpty_SetsExpectedSongCount20() {
        // given
        Performance lineup = Performance.builder().concert(festival()).artist(YUURI).day(1).displayOrder(1).build();
        assertThat(lineup.getExpectedSongCount()).isNull();

        // when
        lineup.changeHeadliner(true);

        // then
        assertThat(lineup.getExpectedSongCount()).isEqualTo(20);
    }

    @Test
    @DisplayName("헤드라이너로 지정해도 이미 정한 예상 곡 수는 바꾸지 않는다")
    void changeHeadliner_WhenAlreadySet_KeepsExpectedSongCount() {
        // given
        Performance lineup = Performance.builder().concert(festival()).artist(YUURI).day(1).displayOrder(1).build();
        lineup.changeExpectedSongCount(12);

        // when
        lineup.changeHeadliner(true);

        // then
        assertThat(lineup.getExpectedSongCount()).isEqualTo(12);
    }
}
