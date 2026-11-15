package com.loop.loop_backend.ConcertImport.kopis;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// KOPIS 공연 시간 안내(dtguidance) → DAY별 공연 시각 기본값 규칙.
// 문구는 모두 실제 KOPIS 대중음악 공연(2026-10 조회)의 값과 기간 그대로다.
// - 공연 기간의 날짜마다 그 요일의 시각을 넣는다. 요일 범위("토요일 ~ 일요일")도 같다.
// - 요일로 못 채운 날짜는 HOL(공휴일) 시각을 쓴다.
// - 하루 여러 회차, 해당 요일 없음, 읽을 수 없는 문구는 그 DAY를 비운다(null). 기간을 모르면 DAY 자체가 없다.
class KopisShowtimesTest {

    private static LocalTime t(int hour, int minute) {
        return LocalTime.of(hour, minute);
    }

    private static List<LocalTime> parse(String dtguidance, String start, String end) {
        return KopisShowtimes.startTimesByDay(dtguidance, LocalDate.parse(start), LocalDate.parse(end));
    }

    // ---------- 요일 ----------

    @Test
    void 하루_공연은_그_요일의_시각이다() {
        // PM Kenobi Live [서울]
        assertThat(parse("토요일(19:00)", "2026-11-28", "2026-11-28")).containsExactly(t(19, 0));
    }

    @Test
    void 여러_요일이면_날짜마다_그_요일의_시각이다() {
        // Vaundy ASIA ARENA TOUR, KWON JIN AH LIVE TOUR
        assertThat(parse("토요일(17:00), 일요일(16:00)", "2026-09-19", "2026-09-20"))
                .containsExactly(t(17, 0), t(16, 0));
        assertThat(parse("금요일(20:00), 토요일(18:00), 일요일(17:00)", "2026-11-06", "2026-11-08"))
                .containsExactly(t(20, 0), t(18, 0), t(17, 0));
    }

    @Test
    void 요일_범위면_범위_안의_요일에_같은_시각이다() {
        // ano LIVE [서울]
        assertThat(parse("토요일 ~ 일요일(18:00)", "2026-10-17", "2026-10-18"))
                .containsExactly(t(18, 0), t(18, 0));
    }

    @Test
    void 문구의_요일_순서가_날짜_순서와_달라도_요일로_맞춘다() {
        // 자라 라슨 첫 단독 내한공연: 10/4(일) ~ 10/5(월)
        assertThat(parse("월요일(19:30), 일요일(19:30)", "2026-10-04", "2026-10-05"))
                .containsExactly(t(19, 30), t(19, 30));
    }

    // ---------- 공휴일(HOL) ----------

    @Test
    void 요일로_못_채운_날짜는_공휴일_시각을_쓴다() {
        // JX TOUR CONCERT: 10/9(금, 한글날) ~ 10/11(일)
        assertThat(parse("HOL(18:00), 토요일 ~ 일요일(17:00)", "2026-10-09", "2026-10-11"))
                .containsExactly(t(18, 0), t(17, 0), t(17, 0));
        // GUMMY Tour Concert [광주]: 12/25(금, 성탄절) ~ 12/26(토)
        assertThat(parse("HOL(19:30), 토요일(18:00)", "2026-12-25", "2026-12-26"))
                .containsExactly(t(19, 30), t(18, 0));
    }

    @Test
    void 공휴일만_있는_하루_공연은_그_시각이다() {
        // 청명: 정상 - 10/5(월)
        assertThat(parse("HOL(18:00)", "2026-10-05", "2026-10-05")).containsExactly(t(18, 0));
    }

    // ---------- 비워 두는 경우 ----------

    @Test
    void 하루_여러_회차면_그_DAY는_비운다() {
        // 무명전설 크리스마스 콘서트, 미스터트롯3 TOP7 콘서트
        assertThat(parse("금요일(13:00,18:00)", "2026-12-25", "2026-12-25")).containsExactly((LocalTime) null);
        assertThat(parse("토요일 ~ 일요일(13:00,18:00)", "2026-11-14", "2026-11-15")).containsExactly(null, null);
    }

    @Test
    void 문구에_없는_요일의_날짜는_비운다() {
        // GLOBAL CALLING: CAMELPHAT - 11/12(목) ~ 11/13(금), 목요일 밤 공연이 금요일 새벽까지 이어짐
        assertThat(parse("목요일(23:00)", "2026-11-12", "2026-11-13")).containsExactly(t(23, 0), null);
    }

    @Test
    void 읽을_수_없는_문구나_빈_문구는_모든_DAY를_비운다() {
        assertThat(parse("추후 공지", "2026-11-14", "2026-11-15")).containsExactly(null, null);
        assertThat(parse(null, "2026-11-14", "2026-11-15")).containsExactly(null, null);
        assertThat(parse("  ", "2026-11-14", "2026-11-14")).containsExactly((LocalTime) null);
    }

    @Test
    void 시각을_읽을_수_없는_요일은_비운다() {
        assertThat(parse("토요일(25:00), 일요일(17:00)", "2026-10-17", "2026-10-18")).containsExactly(null, t(17, 0));
    }

    @Test
    void 기간을_모르면_DAY가_없다() {
        assertThat(KopisShowtimes.startTimesByDay("토요일(19:00)", null, LocalDate.of(2026, 11, 28))).isEmpty();
        assertThat(KopisShowtimes.startTimesByDay("토요일(19:00)", LocalDate.of(2026, 11, 28), null)).isEmpty();
    }

    @Test
    void DAY_개수는_공연_일수와_같다() {
        // 제5회 전주미니재즈페스티벌처럼 기간이 긴 공연도 매일이 DAY이고, 문구의 요일(금·토)만 채워진다
        List<LocalTime> days = parse("금요일(19:30), 토요일(19:30)", "2026-04-18", "2026-04-24");
        assertThat(days).containsExactlyElementsOf(Arrays.asList(
                t(19, 30),                       // 4/18 토
                null, null, null, null, null,    // 4/19 일 ~ 4/23 목
                t(19, 30)));                     // 4/24 금
    }
}