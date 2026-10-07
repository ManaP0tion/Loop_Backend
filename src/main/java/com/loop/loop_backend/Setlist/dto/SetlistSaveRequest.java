package com.loop.loop_backend.Setlist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** 셋리스트 저장(생성 또는 통째 교체). 실제 셋리스트(ACTUAL)는 songIds만 보내고 헤더는 무시된다. */
@Schema(description = "셋리스트 저장 요청. songIds 순서가 곧 공연 순서")
public record SetlistSaveRequest(

        @Size(max = 200)
        @Schema(description = "지난 셋리스트: 투어명. 없으면 null(화면에서 생략)", example = "Yuuri Arena Tour 2026",
                types = {"string", "null"})
        String tourName,

        @Schema(description = "지난 셋리스트: 공연 날짜(필수)", example = "2026-05-01", types = {"string", "null"})
        LocalDate performedOn,

        @Size(max = 200)
        @Schema(description = "지난 셋리스트: 공연 장소(필수)", example = "Zepp Haneda", types = {"string", "null"})
        String venueName,

        @NotEmpty
        @Schema(description = "곡 PK 목록(공연 순서대로). 공연 아티스트의 곡만, 같은 곡 중복(앵코르) 허용. " +
                "목록에 없는 곡은 곡 추가 API(POST /api/admin/artists/{artistId}/songs)로 먼저 만든다",
                example = "[12, 7, 12]", requiredMode = Schema.RequiredMode.REQUIRED)
        List<@NotNull Long> songIds
) {
}
