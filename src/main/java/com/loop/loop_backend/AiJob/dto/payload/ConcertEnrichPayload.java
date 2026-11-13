package com.loop.loop_backend.AiJob.dto.payload;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.loop.loop_backend.Concert.domain.ConcertCategory;

import java.time.LocalDate;
import java.util.List;

/**
 * CONCERT_ENRICH payload = AI 서버 POST /api/ai/concerts/enrich 요청 본문.
 * 목록 필드는 null이면 워커가 INVALID_PAYLOAD로 실패하므로 null을 빈 목록으로 바꿔 둔다.
 * 날짜는 yyyy-MM-dd 문자열만(약속).
 */
public record ConcertEnrichPayload(
        Long concertId,
        String kopisId,
        String title,
        List<String> titleAliases,   // 한글 별칭이 앞에 오도록 정렬된 목록
        ConcertCategory category,
        List<AiArtistPayload> artists,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate startDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate endDate,
        String venueName,
        List<String> knownVendors
) {
    public ConcertEnrichPayload {
        titleAliases = titleAliases == null ? List.of() : List.copyOf(titleAliases);
        artists = artists == null ? List.of() : List.copyOf(artists);
        knownVendors = knownVendors == null ? List.of() : List.copyOf(knownVendors);
    }
}