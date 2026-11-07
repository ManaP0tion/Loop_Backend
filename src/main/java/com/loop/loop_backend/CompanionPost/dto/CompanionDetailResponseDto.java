package com.loop.loop_backend.CompanionPost.dto;

import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.List;

@Getter
@Schema(description = "동행 프로필 상세 응답 DTO (작성자 개인 프로필 정보 포함)")
public class CompanionDetailResponseDto extends CompanionResponseDto {

    @Schema(description = "작성자 해시태그 목록")
    private final List<HashtagSummary> hashtags;

    // 구버전 앱 호환용으로 남겨둔다. 새 화면은 이미지까지 담긴 preferredArtists를 쓴다
    @Schema(description = "작성자 관심 아티스트 이름 목록 (preferredArtists와 같은 순서)")
    private final List<String> preferredArtistNames;

    @Schema(description = "작성자 관심 아티스트 목록 (이름·이미지)")
    private final List<PreferredArtist> preferredArtists;

    public CompanionDetailResponseDto(CompanionPost post, List<HashtagSummary> hashtags,
                                       List<PreferredArtist> preferredArtists, boolean hearted) {
        super(post, hearted);
        this.hashtags = hashtags;
        this.preferredArtists = preferredArtists;
        this.preferredArtistNames = preferredArtists.stream().map(PreferredArtist::getName).toList();
    }

    @Getter
    @Schema(description = "관심 아티스트 요약 정보")
    public static class PreferredArtist {

        @Schema(description = "아티스트 PK", example = "3")
        private final Long artistId;

        @Schema(description = "아티스트 이름", example = "YOASOBI")
        private final String name;

        @Schema(description = "아티스트 이미지 URL (없으면 null)")
        private final String imageUrl;

        public PreferredArtist(Long artistId, String name, String imageUrl) {
            this.artistId = artistId;
            this.name = name;
            this.imageUrl = imageUrl;
        }
    }

    @Getter
    @Schema(description = "해시태그 요약 정보")
    public static class HashtagSummary {

        @Schema(description = "해시태그 ID", example = "1")
        private final Long id;

        @Schema(description = "해시태그", example = "굿즈")
        private final String tag;

        public HashtagSummary(Long id, String tag) {
            this.id = id;
            this.tag = tag;
        }
    }
}