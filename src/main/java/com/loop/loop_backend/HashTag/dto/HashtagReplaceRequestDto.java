package com.loop.loop_backend.HashTag.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "해시태그 목록 교체 요청 DTO")
public class HashtagReplaceRequestDto {

    // 개수(최대 5개)와 중복은 기존 추가 API와 같은 409로 맞추기 위해 서비스에서 검사한다
    @NotNull(message = "태그 목록은 필수입니다")
    @ArraySchema(arraySchema = @Schema(description = "저장할 해시태그 목록 (최대 5개, []는 모두 삭제)", example = "[\"낯가림\", \"계획형\"]"))
    private List<
            @NotNull(message = "태그는 필수입니다")
            @Size(min = 1, max = 5, message = "태그는 1~5자여야 합니다.")
            @Pattern(regexp = "^[a-zA-Z0-9가-힣]+$", message = "태그에 특수문자를 사용할 수 없습니다.")
                    String> tags;

    public void setTags(List<String> tags) {
        this.tags = tags == null ? null : tags.stream()
                .map(tag -> tag == null ? null : tag.strip())
                .toList();
    }
}