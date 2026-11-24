package com.loop.loop_backend.FavoriteArtist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "관심 아티스트 일괄 교체 요청 DTO")
public class FavoriteArtistReplaceRequestDto {

    // 개수(1~5개)는 성격 교체·추가 API와 같은 전용 코드(FAVORITE_ARTIST_REQUIRED 400 / LIMIT_FAVORITE_ARTIST 409)로
    // 맞추고 중복 id를 합친 뒤에 세기 위해 서비스에서 검사한다
    @NotNull(message = "관심 아티스트 목록은 필수입니다")
    @Schema(description = "아티스트 PK 목록 (1~5개)", example = "[1, 2, 3, 4, 5]")
    private List<@NotNull(message = "아티스트 ID는 필수입니다") Long> artistIds;
}
