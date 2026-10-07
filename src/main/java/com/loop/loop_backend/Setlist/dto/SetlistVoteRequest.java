package com.loop.loop_backend.Setlist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "예상 셋리스트 투표. 기존 선택을 통째로 교체한다")
public record SetlistVoteRequest(
        @NotEmpty
        @Schema(description = "고른 곡 PK 목록. 1곡 이상, 최대 n곡(예상 곡 수). 중복은 하나로 본다", example = "[12, 7, 3]",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<@NotNull Long> songIds
) {
}
