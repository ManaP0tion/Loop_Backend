package com.loop.loop_backend.FavoriteArtist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "관심 아티스트 등록 요청 DTO")
public class FavoriteArtistCreateRequestDto {

    @NotNull(message = "아티스트 ID는 필수입니다")
    @Schema(description = "아티스트 PK", example = "10")
    private Long artistId;
}