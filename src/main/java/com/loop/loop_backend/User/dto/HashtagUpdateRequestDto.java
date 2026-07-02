package com.loop.loop_backend.User.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "관심 해시태그 수정 요청 DTO")
public class HashtagUpdateRequestDto {

    @NotEmpty(message = "해시태그는 최소 1개 이상이어야 합니다")
    @Schema(description = "해시태그 목록", example = "[\"발라드\", \"힙합\"]")
    private List<String> hashtags;
}