package com.loop.loop_backend.User.dto;

import com.loop.loop_backend.User.domain.AgeGroup;
import com.loop.loop_backend.User.domain.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "사용자 정보 수정 요청 DTO")
public class UserUpdateRequestDto {

    @NotBlank(message = "닉네임은 필수입니다")
    @Size(max = 50, message = "닉네임은 50자 이하여야 합니다")
    @Schema(description = "닉네임", example = "새닉네임")
    private String nickname;

    @Email(message = "이메일 형식이 올바르지 않습니다")
    @Size(max = 255)
    @Schema(description = "이메일", example = "new@example.com")
    private String email;

    @NotNull(message = "성별은 필수입니다")
    @Schema(description = "성별", example = "MALE")
    private Gender gender;

    @NotNull(message = "연령대는 필수입니다")
    @Schema(description = "연령대", example = "AGE_30S")
    private AgeGroup ageGroup;
}