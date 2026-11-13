package com.loop.loop_backend.AiJob.dto.payload;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * FESTIVAL_LINEUP payload = AI 서버 POST /api/ai/festivals/lineup 요청 본문.
 * startDate가 DAY 1 기준이라 필수 - 없으면 작업을 만들지 않는다(서비스에서 400).
 */
public record FestivalLineupPayload(
        Long concertId,
        String title,
        List<String> titleAliases,   // 한글 별칭이 앞에 오도록 정렬된 목록
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate startDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate endDate,
        String venueName
) {
    public FestivalLineupPayload {
        titleAliases = titleAliases == null ? List.of() : List.copyOf(titleAliases);
    }
}