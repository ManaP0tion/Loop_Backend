package com.loop.loop_backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "관리자 2단계 인증 확인 요청 DTO")
public class AdminTwoFactorVerifyRequestDto {

    @NotBlank(message = "challengeId는 필수입니다")
    @Schema(description = "카카오 로그인 응답으로 받은 challenge 식별자")
    private String challengeId;

    @NotBlank(message = "인증 코드는 필수입니다")
    @Schema(description = "관리자 이메일로 발송된 6자리 코드", example = "123456")
    private String code;
}
