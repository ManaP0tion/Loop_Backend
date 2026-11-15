package com.loop.loop_backend.Concert.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertShowtime;
import com.loop.loop_backend.ConcertImport.kopis.KopisShowtimes;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 공개 응답의 DAY별 공연 시각(예정·지난 상세 공통). */
@Schema(description = "DAY별 공연 시각")
public record ConcertShowtimeDto(

        @Schema(description = "DAY 번호(1부터)", requiredMode = Schema.RequiredMode.REQUIRED)
        int day,

        @Schema(description = "날짜", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate date,

        @Schema(description = "시작 시각(HH:mm). 미정이면 null", types = {"string", "null"})
        @JsonFormat(pattern = "HH:mm")
        LocalTime startTime
) {

    /**
     * 공연 기간의 모든 DAY를 순서대로 돌려준다(기간이 없으면 빈 목록).
     * 저장된 공연 시각이 하나도 없는 공연(이 기능 전에 승인된 공연)은 KOPIS 공연 시간 안내를 승인 때와 같은 규칙으로 변환한다.
     * 저장할 때는 미정인 DAY도 행이 남으므로, 관리자가 시각을 모두 비운 공연이 KOPIS 값으로 되살아나지는 않는다.
     */
    public static List<ConcertShowtimeDto> byDay(Concert concert) {
        LocalDate start = concert.getStartDate();
        LocalDate end = concert.getEndDate();
        if (start == null || end == null) return List.of();

        Map<LocalDate, LocalTime> timeByDate = new HashMap<>();
        if (concert.getShowtimes().isEmpty()) {
            List<LocalTime> parsed = KopisShowtimes.startTimesByDay(concert.getShowtime(), start, end);
            for (int i = 0; i < parsed.size(); i++) {
                timeByDate.put(start.plusDays(i), parsed.get(i));
            }
        } else {
            for (ConcertShowtime showtime : concert.getShowtimes()) {
                timeByDate.put(showtime.getDate(), showtime.getStartTime());
            }
        }

        List<ConcertShowtimeDto> result = new ArrayList<>();
        int day = 1;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            result.add(new ConcertShowtimeDto(day++, date, timeByDate.get(date)));
        }
        return result;
    }
}