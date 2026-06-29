package com.loop.loop_backend.User.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "비밀번호 변경 요청 DTO")
public class PasswordChangeRequestDto {

    @NotBlank(message = "현재 비밀번호는 필수입니다")
    @Schema(description = "현재 비밀번호", example = "oldPassword1!")
    private String currentPassword;

    @NotBlank(message = "새 비밀번호는 필수입니다")
    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$",
            message = "비밀번호는 영문, 숫자, 특수문자(@$!%*#?&)를 모두 포함해야 합니다"
    )
    @Schema(description = "새 비밀번호 (영문+숫자+특수문자 8자 이상)", example = "newPassword1!")
    private String newPassword;

    @NotBlank(message = "새 비밀번호 확인은 필수입니다")
    @Schema(description = "새 비밀번호 확인", example = "newPassword1!")
    private String newPasswordConfirm;
}