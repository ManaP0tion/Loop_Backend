package com.loop.loop_backend.User.dto;

import com.loop.loop_backend.User.AgeGroup;
import com.loop.loop_backend.User.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "카카오 회원가입 요청 DTO")
public class KakaoRegisterRequestDto {

    @NotBlank(message = "카카오 provider_id는 필수입니다")
    @Schema(description = "카카오 고유 회원번호", example = "1234567890")
    private String providerId;

    @Email(message = "이메일 형식이 올바르지 않습니다")
    @Size(max = 255)
    @Schema(description = "카카오 이메일 (선택)", example = "kakao@example.com")
    private String email;

    @NotBlank(message = "닉네임은 필수입니다")
    @Size(max = 50, message = "닉네임은 50자 이하여야 합니다")
    @Schema(description = "닉네임", example = "루퍼123")
    private String nickname;
    @NotNull(message = "성별은 필수입니다")
    @Schema(description = "성별", example = "FEMALE")
    private Gender gender;

    @NotNull(message = "연령대는 필수입니다")
    @Schema(description = "연령대", example = "AGE_20S")
    private AgeGroup ageGroup;
}
