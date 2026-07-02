package com.loop.loop_backend.User.dto;

import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Gender;
import com.loop.loop_backend.User.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Schema(description = "사용자 응답 DTO")
public class UserResponseDto {

    @Schema(description = "사용자 PK", example = "1")
    private final Long id;

    @Schema(description = "가입 경로", example = "EMAIL")
    private final AuthProvider authProvider;

    @Schema(description = "로그인 아이디 (이메일 계정)", example = "user123")
    private final String userId;

    @Schema(description = "이메일", example = "user@example.com")
    private final String email;

    @Schema(description = "닉네임", example = "루퍼123")
    private final String nickname;

    @Schema(description = "성별", example = "MALE")
    private final Gender gender;

    @Schema(description = "생년월일", example = "2000-01-01")
    private final LocalDate birthDate;

    @Schema(description = "온보딩 완료 여부", example = "true")
    private final boolean onboardingCompleted;

    @Schema(description = "생성일시")
    private final LocalDateTime createdAt;

    @Schema(description = "수정일시")
    private final LocalDateTime updatedAt;

    public UserResponseDto(User user) {
        this.id = user.getId();
        this.authProvider = user.getAuthProvider();
        this.userId = user.getUserId();
        this.email = user.getEmail();
        this.nickname = user.getNickname();
        this.gender = user.getGender();
        this.birthDate = user.getBirthDate();
        this.onboardingCompleted = user.isOnboardingCompleted();
        this.createdAt = user.getCreatedAt();
        this.updatedAt = user.getUpdatedAt();
    }
}
