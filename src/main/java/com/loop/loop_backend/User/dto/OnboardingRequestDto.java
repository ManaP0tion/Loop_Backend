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

    // 카카오 첫 로그인 때 랜덤 닉네임이 이미 들어가 있어서 보내지 않으면 그대로 쓴다
    @Size(max = 8, message = "닉네임은 8자 이하여야 합니다.")
    @Pattern(regexp = "^[a-zA-Z0-9가-힣]+$", message = "닉네임에 특수문자를 사용할 수 없습니다.")
    @Schema(description = "닉네임(선택). 보내지 않으면 가입 때 받은 랜덤 닉네임을 그대로 쓴다",
            example = "달빛군청", types = {"string", "null"})
    private String nickname;

    @NotNull(message = "생년월일은 필수입니다")
    @Past(message = "생년월일은 과거 날짜여야 합니다")
    @Schema(description = "생년월일", example = "2000-01-01")
    private LocalDate birthDate;

    @NotNull(message = "성별은 필수입니다")
    @Schema(description = "성별", example = "MALE")
    private Gender gender;

    @Schema(description = "프로필 이미지 URL(선택). POST /api/users/me/profile-image가 돌려준 URL. 없으면 null",
            types = {"string", "null"})
    private String profileImageUrl;
}
