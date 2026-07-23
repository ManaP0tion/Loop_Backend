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

    @NotBlank(message = "리다이렉트 주소는 필수입니다")
    @Schema(description = "프론트가 카카오 인가 요청 시 사용한 redirect_uri (서버에 등록된 값과 정확히 일치해야 함)",
            example = "https://loop.io.kr")
    private String redirectUri;
}
