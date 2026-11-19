package com.loop.loop_backend.AiJob.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.loop.loop_backend.AiJob.domain.AiJobFeedback;
import com.loop.loop_backend.AiJob.domain.AiJobFeedbackOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 관리자 처리 결과(피드백). 어드민이 저장에 성공한 직후 작업 1건당 한 번 보낸다.
 * 다시 보내면 그 작업의 피드백을 통째로 바꾼다. items가 비어 있으면 "저장했지만 AI가 채운 항목이 없었음".
 */
@Schema(description = "AI 결과 처리 결과(피드백)")
public record AdminAiJobFeedbackRequest(

        @NotNull
        @Size(max = 1000)
        @Valid
        @Schema(description = "항목별 처리 결과. 빈 목록 가능", requiredMode = Schema.RequiredMode.REQUIRED)
        List<Item> items
) {

    @Schema(description = "AI 결과 항목 하나의 처리 결과")
    public record Item(

            // 형식은 검사하지 않는다 - 어드민 화면의 행 식별자를 그대로 쓴다
            @NotBlank
            @Size(max = AiJobFeedback.KEY_MAX_LENGTH)
            @Schema(description = "항목 키(어드민 화면의 행 식별자). 숫자는 AI 결과 배열의 0부터 세는 번호. " +
                    "CONCERT_ENRICH: presales.{n}.opens, generalSales.{n}.vendors, showtime.{yyyy-MM-dd}, link.officialSiteUrl 등 / " +
                    "FESTIVAL_LINEUP: d{day}-{n}, u-{n} / SETLIST_ACTUAL: songs.{n}",
                    example = "generalSales.0.opens", requiredMode = Schema.RequiredMode.REQUIRED)
            String key,

            @NotNull
            @Schema(description = "MATCHED(기존 값과 같음) / APPLIED(그대로 적용) / EDITED(적용 후 수정) / " +
                    "NOT_APPLIED(적용 안 함) / KEPT_EXISTING(기존 값 유지)",
                    example = "APPLIED", requiredMode = Schema.RequiredMode.REQUIRED)
            AiJobFeedbackOutcome outcome,

            @Schema(description = "EDITED일 때 관리자가 최종 저장한 값(선택). 다른 처리 결과면 무시. 500자를 넘으면 잘라 저장",
                    example = "18:00", types = {"string", "null"})
            String finalValue
    ) {
    }

    /** 같은 키가 두 번이면 어느 처리 결과가 맞는지 알 수 없다. */
    @JsonIgnore
    @AssertTrue(message = "같은 항목 키가 두 번 있습니다.")
    public boolean isKeysUnique() {
        if (items == null) return true;
        Set<String> seen = new HashSet<>();
        return items.stream().filter(Objects::nonNull).map(Item::key).filter(Objects::nonNull).allMatch(seen::add);
    }
}
