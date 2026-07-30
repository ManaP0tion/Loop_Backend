package com.loop.loop_backend.CompanionPost.domain;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CompanionPostTest {

    private CompanionPost postFor(LocalDate concertStartDate, WatchDay watchDay) {
        Concert concert = Concert.builder()
                .title("테스트 콘서트")
                .category(ConcertCategory.DOMESTIC_ARTIST)
                .startDate(concertStartDate)
                .build();
        return CompanionPost.builder()
                .concert(concert)
                .watchDay(watchDay)
                .activities(Set.of(CompanionActivity.MEAL))
                .build();
    }

    @Test
    void 관람일_다음날_오전_9시59분에는_아직_만료되지_않는다() {
        LocalDate watchDate = LocalDate.of(2026, 7, 20);
        CompanionPost post = postFor(watchDate, WatchDay.DAY1);

        boolean expired = post.isExpired(watchDate.plusDays(1).atTime(9, 59));

        assertThat(expired).isFalse();
    }

    @Test
    void 관람일_다음날_오전_10시_정각에_만료된다() {
        LocalDate watchDate = LocalDate.of(2026, 7, 20);
        CompanionPost post = postFor(watchDate, WatchDay.DAY1);

        boolean expired = post.isExpired(watchDate.plusDays(1).atTime(10, 0));

        assertThat(expired).isTrue();
    }

    @Test
    void 관람일_당일_늦은_시각에도_아직_만료되지_않는다() {
        LocalDate watchDate = LocalDate.of(2026, 7, 20);
        CompanionPost post = postFor(watchDate, WatchDay.DAY1);

        boolean expired = post.isExpired(watchDate.atTime(23, 59));

        assertThat(expired).isFalse();
    }

    @Test
    void DAY2_관람일은_콘서트_시작일_다음날로_계산되어_만료_기준도_그에_맞춰_하루_밀린다() {
        LocalDate concertStartDate = LocalDate.of(2026, 7, 20);
        CompanionPost post = postFor(concertStartDate, WatchDay.DAY2); // 관람일 = 7/21

        assertThat(post.isExpired(LocalDate.of(2026, 7, 22).atTime(9, 59))).isFalse();
        assertThat(post.isExpired(LocalDate.of(2026, 7, 22).atTime(10, 0))).isTrue();
    }

    @Test
    void 콘서트_날짜가_미정이면_영원히_만료되지_않는다() {
        CompanionPost post = postFor(null, WatchDay.DAY1);

        assertThat(post.isExpired(LocalDateTime.of(2099, 1, 1, 0, 0))).isFalse();
    }
}
