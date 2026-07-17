package com.loop.loop_backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "카카오 로그인 요청 DTO")
public class KakaoLoginRequestDto {

    @NotBlank(message = "인가 코드는 필수입니다")
    @Schema(description = "카카오 인가 코드", example = "abcd1234")
    private String code;
}
