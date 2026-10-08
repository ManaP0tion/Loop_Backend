package com.loop.loop_backend.Setlist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 곡 선택 화면(NO.57·64). 순위·득표 수는 넣지 않는다(상위권 쏠림 방지).
 * 후보 전체를 정렬 순번대로 한 번에 내려준다 - 50곡·20곡 단위 더보기와 세 표기 검색은 프론트가 이 목록으로 한다.
 */
@Schema(description = "예상 셋리스트 후보. 셋리스트를 운영하지 않는 공연(페스티벌·예상 곡 수 없음)이면 songs가 빈 목록")
public record SetlistCandidatesResponse(
        @Schema(description = "최대 선택 곡 수 n(예상 곡 수). 셋리스트를 운영하지 않는 공연이면 null", example = "20",
                types = {"integer", "null"})
        Integer maxSelect,

        @Schema(description = "투표 마감 여부(공연 시작일 00:00 KST부터 true)", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean votingClosed,

        @Schema(description = "후보 곡 전체(정렬 순번대로)", requiredMode = Schema.RequiredMode.REQUIRED)
        List<SongCandidateResponse> songs
) {
}
