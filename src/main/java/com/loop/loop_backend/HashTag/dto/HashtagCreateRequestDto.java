package com.loop.loop_backend.HashTag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;

@Getter
@NoArgsConstructor
@Schema(description = "해시태그 생성 요청 DTO")
public class HashtagCreateRequestDto {

    @NotNull(message = "태그는 필수입니다")
    @Size(max = 5, message = "태그는 5자 이하여야 합니다.")
    @Pattern(regexp = "^[a-zA-Z0-9가-힣]+$", message = "태그에 특수문자를 사용할 수 없습니다.")
    @Schema(description = "해시태그", example = "굿즈")
    private String tag;

    public void setTag(String tag) {
        this.tag = tag == null ? null : tag.strip();
    }
}