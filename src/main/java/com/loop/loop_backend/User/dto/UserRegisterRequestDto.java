package com.loop.loop_backend.User.dto;

import com.loop.loop_backend.User.domain.AgeGroup;
import com.loop.loop_backend.User.domain.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "이메일 회원가입 요청 DTO")
public class UserRegisterRequestDto {

    @NotBlank(message = "아이디는 필수입니다")
    @Size(min = 4, max = 50, message = "아이디는 4~50자여야 합니다")
    @Schema(description = "로그인 아이디", example = "user123")
    private String userId;

    @NotBlank(message = "비밀번호는 필수입니다")
    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다")
    @Schema(description = "비밀번호", example = "password123!")
    private String password;

    @Email(message = "이메일 형식이 올바르지 않습니다")
    @Size(max = 255)
    @Schema(description = "이메일 (선택)", example = "user@example.com")
    private String email;

    @NotBlank(message = "닉네임은 필수입니다")
    @Size(max = 50, message = "닉네임은 50자 이하여야 합니다")
    @Schema(description = "닉네임", example = "루퍼123")
    private String nickname;

    @NotNull(message = "성별은 필수입니다")
    @Schema(description = "성별", example = "MALE")
    private Gender gender;

    @NotNull(message = "연령대는 필수입니다")
    @Schema(description = "연령대", example = "AGE_20S")
    private AgeGroup ageGroup;
}
