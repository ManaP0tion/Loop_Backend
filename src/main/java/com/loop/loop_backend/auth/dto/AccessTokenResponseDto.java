package com.loop.loop_backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "Access Token 응답 DTO")
public class AccessTokenResponseDto {

    @Schema(description = "Access Token")
    private final String accessToken;

    public AccessTokenResponseDto(String accessToken) {
        this.accessToken = accessToken;
    }
}