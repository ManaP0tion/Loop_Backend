package com.loop.loop_backend.Storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "이미지 업로드 응답 DTO")
public class ImageUploadResponseDto {

    @Schema(description = "업로드된 이미지의 공개 URL")
    private final String url;

    public ImageUploadResponseDto(String url) {
        this.url = url;
    }
}