package com.loop.loop_backend.ConcertImport.dto;

import com.loop.loop_backend.Concert.domain.ConcertCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * KOPIS 검색 결과에서 고른 공연을 직접 등록한다. 공연 정보는 서버가 kopisId로 KOPIS에서 다시 가져온다.
 * 검증 실패는 필드 구분 없이 400 하나로 응답한다.
 */
@Schema(description = "KOPIS 직접 등록 요청. 필수: kopisId, category")
public record ManualImportRequest(

        // KOPIS 요청 경로(/pblprfr/{kopisId})에 그대로 들어가므로 영문·숫자만 받는다
        @NotBlank
        @Size(max = 20)
        @Pattern(regexp = "^[A-Za-z0-9]+$")
        @Schema(description = "KOPIS 공연 ID (검색 결과의 kopisId). 영문·숫자만", example = "PF287093",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String kopisId,

        @NotNull
        @Schema(description = "공연 유형. J_POP_ARTIST(내한 공연) 또는 JAPAN_FESTIVAL(페스티벌). 그 외 값은 400",
                example = "J_POP_ARTIST", requiredMode = Schema.RequiredMode.REQUIRED)
        ConcertCategory category,

        @Schema(description = "아티스트 PK 목록. 내한 공연은 0~1명(비우면 아티스트 없이 등록, 공개 전 공연 수정에서 지정), " +
                "페스티벌은 비워야 한다(라인업은 등록 후 라인업 관리에서 추가). 어기면 400",
                example = "[3]", types = {"array", "null"})
        List<Long> artistIds) {

    /** null은 빈 목록으로 본다 */
    public List<Long> artistIdsOrEmpty() {
        return artistIds == null ? List.of() : artistIds;
    }
}