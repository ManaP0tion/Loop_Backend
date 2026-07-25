package com.loop.loop_backend.User.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "닉네임 사용 가능 여부 응답 DTO")
public class NicknameCheckResponseDto {

    @Schema(description = "사용 가능 여부 (true=사용 가능, false=이미 사용중)", example = "true")
    private final boolean available;

    public NicknameCheckResponseDto(boolean available) {
        this.available = available;
    }
}