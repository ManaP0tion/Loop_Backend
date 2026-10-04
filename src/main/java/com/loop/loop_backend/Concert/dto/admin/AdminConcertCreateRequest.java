package com.loop.loop_backend.Concert.dto.admin;

import com.loop.loop_backend.Concert.domain.ConcertCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 공연 직접 등록 요청 (KOPIS에 없는 공연). 항상 비공개로 만들어진다 - 포스터는 공연 id가 생긴 뒤 업로드할 수 있어서,
 * 등록 → 포스터 업로드 → 수정(PATCH)에서 공개로 전환하는 순서가 된다. 공개할 때 필수값을 검사한다.
 */
@Schema(description = "공연 직접 등록 요청. 비공개로 생성되고, 공개는 수정 API에서 한다. 필수: category, title")
public record AdminConcertCreateRequest(

        @NotNull
        @Schema(description = "공연 유형 (필수). J_POP_ARTIST(내한 공연) / JAPAN_FESTIVAL(페스티벌)",
                example = "J_POP_ARTIST", requiredMode = Schema.RequiredMode.REQUIRED)
        ConcertCategory category,

        @NotBlank
        @Size(max = 255)
        @Schema(description = "공연명 (필수)", example = "YUURI LIVE [서울]", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @Schema(description = "공연명 별칭(검색용). 공백·중복은 정리된다", example = "[\"유우리\"]", types = {"array", "null"})
        List<@Size(max = 255) String> titleAliases,

        @Schema(description = "공연 시작일", example = "2026-12-05", types = {"string", "null"})
        LocalDate startDate,

        @Schema(description = "공연 종료일. 하루 공연이면 시작일과 같다", example = "2026-12-06", types = {"string", "null"})
        LocalDate endDate,

        @Schema(description = "DAY 순서대로의 공연 시각(HH:mm). 개수는 공연 일수와 같아야 하고, 미정인 DAY는 null. 기간이 있어야 보낼 수 있다",
                example = "[\"18:00\", null]", types = {"array", "null"})
        List<LocalTime> showtimes,

        @Schema(description = "공연장 id (공연장 관리)", example = "5", types = {"integer", "null"})
        Long venueId,

        @Size(max = 1)
        @Schema(description = "아티스트 id 목록. 내한 공연만 지정하며 지금은 최대 1명. 페스티벌은 비워 둔다",
                example = "[12]", types = {"array", "null"})
        List<Long> artistIds,

        @Schema(description = "예상 곡 수. 내한 공연만, 1 이상", example = "20", types = {"integer", "null"})
        Integer expectedSongCount,

        @Schema(description = "숙소 섹션 노출 여부. 켜려면 lodgingUrl이 있어야 한다. 기본 false", example = "false", types = {"boolean", "null"})
        Boolean lodgingVisible,

        @Size(max = 1000)
        @Schema(description = "숙소 딥링크(완성된 URL)", types = {"string", "null"})
        String lodgingUrl,

        @Schema(description = "관련 상품 코드(CD Japan). 공백·중복은 정리된다", example = "[\"PCXP-51237\"]", types = {"array", "null"})
        List<@Size(max = 100) String> productCodes
) {
}