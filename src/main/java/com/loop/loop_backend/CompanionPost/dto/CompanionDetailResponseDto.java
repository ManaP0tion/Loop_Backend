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

    @Schema(description = "작성자 관심 아티스트 ID 목록 (아직 연관관계 미구현으로 항상 빈 배열)")
    private final List<String> preferredArtistIds;

    public CompanionDetailResponseDto(CompanionPost post, List<HashtagSummary> hashtags, boolean hearted) {
        super(post, hearted);
        this.hashtags = hashtags;
        this.preferredArtistIds = List.of();
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