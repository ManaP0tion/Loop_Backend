package com.loop.loop_backend.AiJob.dto.payload;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.loop.loop_backend.Concert.domain.ConcertCategory;

import java.time.LocalDate;
import java.util.List;

/**
 * SETLIST_ACTUAL payload = AI 서버 셋리스트 요청 본문. 출연 정보(performance) 하나 = 아티스트 한 명의 셋리스트.
 * 단독 공연은 공연 기간, 페스티벌은 그 헤드라이너가 출연하는 DAY의 날짜(첫 DAY ~ 마지막 DAY)를 넣는다.
 * 목록 필드는 null 대신 빈 목록, 날짜는 yyyy-MM-dd 문자열(약속).
 */
public record SetlistActualPayload(
        Long concertId,
        ConcertCategory category,
        String title,                  // 페스티벌이면 페스티벌명
        List<String> titleAliases,     // 한글 별칭이 앞에 오도록 정렬된 목록. AI가 첫 값을 한국어 공연명으로 검색어에 쓴다
        AiArtistPayload artist,        // 그 출연 정보의 아티스트(페스티벌이면 헤드라이너)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate startDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate endDate,
        String venueName,
        Integer expectedSongCount,     // 출연 정보의 예상 곡 수. 없으면 null
        List<AiSongPayload> songs      // 그 아티스트의 곡 전체(삭제된 곡 제외). 비어 있으면 AI는 제목만 준다
) {
    public SetlistActualPayload {
        titleAliases = titleAliases == null ? List.of() : List.copyOf(titleAliases);
        songs = songs == null ? List.of() : List.copyOf(songs);
    }
}
