package com.loop.loop_backend.Setlist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "내 예상 셋리스트 투표. 투표하지 않았으면 voted=false, songIds 빈 목록")
public record MySetlistVoteResponse(
        @Schema(description = "투표 여부", requiredMode = Schema.RequiredMode.REQUIRED) boolean voted,
        @Schema(description = "고른 곡 PK(정렬 순번대로). 곡 선택 화면에 체크된 상태로 쓴다", requiredMode = Schema.RequiredMode.REQUIRED)
        List<Long> songIds,
        @Schema(description = "결과 메일 수신 동의 여부. true면 투표 후 동의 모달을 띄우지 않는다", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean resultMailConsent
) {
    public static MySetlistVoteResponse none() {
        return new MySetlistVoteResponse(false, List.of(), false);
    }
}
