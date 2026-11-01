package com.loop.loop_backend.FavoriteArtist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "관심 아티스트 일괄 교체 요청 DTO")
public class FavoriteArtistReplaceRequestDto {

    @NotEmpty(message = "관심 아티스트는 최소 1개 선택해야 합니다")
    @Size(max = 5, message = "관심 아티스트는 최대 5개까지 등록할 수 있습니다")
    @Schema(description = "아티스트 PK 목록 (1~5개)", example = "[1, 2, 3, 4, 5]")
    private List<@NotNull(message = "아티스트 ID는 필수입니다") Long> artistIds;
}
