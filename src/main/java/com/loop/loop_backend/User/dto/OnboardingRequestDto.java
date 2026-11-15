package com.loop.loop_backend.User.dto;

import com.loop.loop_backend.User.domain.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@Schema(description = "온보딩 요청 DTO")
public class OnboardingRequestDto {

    @NotNull(message = "닉네임은 필수입니다")
    @Size(max = 5, message = "닉네임은 5자 이하여야 합니다.")
    @Pattern(regexp = "^[a-zA-Z0-9가-힣]+$", message = "닉네임에 특수문자를 사용할 수 없습니다.")
    @Schema(description = "닉네임", example = "nick")
    private String nickname;

    @NotNull(message = "생년월일은 필수입니다")
    @Past(message = "생년월일은 과거 날짜여야 합니다")
    @Schema(description = "생년월일", example = "2000-01-01")
    private LocalDate birthDate;

    @NotNull(message = "성별은 필수입니다")
    @Schema(description = "성별", example = "MALE")
    private Gender gender;
}
