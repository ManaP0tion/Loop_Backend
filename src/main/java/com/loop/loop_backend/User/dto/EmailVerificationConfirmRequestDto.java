package com.loop.loop_backend.User.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "이메일 인증 코드 확인 요청 DTO")
public class EmailVerificationConfirmRequestDto {

    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "이메일 형식이 올바르지 않습니다")
    @Schema(description = "인증 요청한 이메일", example = "user@example.com")
    private String email;

    @NotBlank(message = "인증 코드는 필수입니다")
    @Schema(description = "이메일로 받은 6자리 인증 코드", example = "482910")
    private String code;
}