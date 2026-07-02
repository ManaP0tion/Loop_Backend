package com.loop.loop_backend.User.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "사용자 정보 수정 요청 DTO")
public class UserUpdateRequestDto {

    @NotBlank(message = "닉네임은 필수입니다")
    @Size(max = 5, message = "닉네임은 5자 이하여야 합니다")
    @Pattern(regexp = "^[a-zA-Z0-9가-힣]+$", message = "닉네임에 특수문자를 사용할 수 없습니다")
    @Schema(description = "닉네임", example = "새닉네임")
    private String nickname;

    @Schema(description = "프로필 이미지 url")
    private String profileImageUrl;


}