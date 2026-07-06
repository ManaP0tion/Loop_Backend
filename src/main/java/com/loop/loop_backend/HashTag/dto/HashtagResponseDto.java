package com.loop.loop_backend.HashTag.dto;

import com.loop.loop_backend.HashTag.domain.UserHashtag;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;


@Getter
@Schema(description = "해시태그 응답 DTO")
public class HashtagResponseDto {

    @Schema(description = "해시태그 ID", example = "1")
    private Long id;

    @Schema(description = "해시태그", example = "굿즈")
    private String tag;

    @Builder
    private HashtagResponseDto(Long id, String tag) {
        this.id = id;
        this.tag = tag;
    }

    public static HashtagResponseDto from(UserHashtag entity) {
        return HashtagResponseDto.builder()
                .id(entity.getId())
                .tag(entity.getTag())
                .build();
    }
}