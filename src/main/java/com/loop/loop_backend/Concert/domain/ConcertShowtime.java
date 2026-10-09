package com.loop.loop_backend.Concert.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DAY별 공연 시각(AD-01). DAY는 공연 시작일~종료일로 계산되고, 여기에는 날짜마다 시작 시각만 둔다.
 * DAY 번호가 아니라 날짜로 저장한다 - 공연 기간이 바뀌어도 시각이 엉뚱한 날로 밀리지 않게.
 * 시각이 미정이면 비워 둔다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ConcertShowtime {

    @Column(name = "show_date", nullable = false)
    private LocalDate date;

    @Column(name = "start_time")
    private LocalTime startTime;
}