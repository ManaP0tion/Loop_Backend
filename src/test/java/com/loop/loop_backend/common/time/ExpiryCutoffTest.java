package com.loop.loop_backend.common.time;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ExpiryCutoffTest {

    @Test
    void 오전_10시_이전이면_어제_날짜를_반환한다() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 21, 9, 59);

        assertThat(ExpiryCutoff.cutoffDate(now)).isEqualTo(LocalDate.of(2026, 7, 20));
    }

    @Test
    void 오전_10시_정각부터는_오늘_날짜를_반환한다() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 21, 10, 0);

        assertThat(ExpiryCutoff.cutoffDate(now)).isEqualTo(LocalDate.of(2026, 7, 21));
    }

    @Test
    void 자정_직후에도_어제_날짜를_반환한다() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 21, 0, 0);

        assertThat(ExpiryCutoff.cutoffDate(now)).isEqualTo(LocalDate.of(2026, 7, 20));
    }
}
