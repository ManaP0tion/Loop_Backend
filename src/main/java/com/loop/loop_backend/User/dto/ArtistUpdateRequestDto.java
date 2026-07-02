package com.loop.loop_backend.User.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "관심 아티스트 수정 요청 DTO")
public class ArtistUpdateRequestDto {

    @NotEmpty(message = "아티스트는 최소 1개 이상이어야 합니다")
    @Schema(description = "아티스트 ID 목록", example = "[1, 2, 3]")
    private List<Long> artistIds;
}