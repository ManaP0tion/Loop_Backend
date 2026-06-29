package com.loop.loop_backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RefreshRequestDto {

    @NotBlank(message = "Refresh Token은 필수입니다")
    private String refreshToken;
}