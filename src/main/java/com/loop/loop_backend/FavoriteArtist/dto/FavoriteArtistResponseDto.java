package com.loop.loop_backend.FavoriteArtist.dto;

import com.loop.loop_backend.FavoriteArtist.domain.FavoriteArtist;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Schema(description = "관심 아티스트 응답 DTO")
public class FavoriteArtistResponseDto {

    @Schema(description = "관심 아티스트 등록 ID", example = "1")
    private Long id;

    @Schema(description = "아티스트 PK", example = "10")
    private Long artistId;

    @Schema(description = "아티스트 이름", example = "요네즈 켄시")
    private String artistName;

    @Schema(description = "아티스트 이미지 URL")
    private String artistImageUrl;

    @Builder
    private FavoriteArtistResponseDto(Long id, Long artistId, String artistName, String artistImageUrl) {
        this.id = id;
        this.artistId = artistId;
        this.artistName = artistName;
        this.artistImageUrl = artistImageUrl;
    }

    public static FavoriteArtistResponseDto from(FavoriteArtist entity) {
        return FavoriteArtistResponseDto.builder()
                .id(entity.getId())
                .artistId(entity.getArtist().getId())
                .artistName(entity.getArtist().getName())
                .artistImageUrl(entity.getArtist().getImageUrl())
                .build();
    }
}