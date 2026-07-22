package com.loop.loop_backend.Test.dto;

import com.loop.loop_backend.User.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "테스트 사용자 생성 응답 DTO")
public class TestUserResponseDto {

    @Schema(description = "생성된 사용자 PK", example = "1")
    private final Long userId;

    @Schema(description = "생성된 사용자 닉네임", example = "t1234")
    private final String nickname;

    @Schema(description = "로그인 아이디 (POST /api/auth/login 용 · dev 전용)", example = "admin1234")
    private final String loginId;

    @Schema(description = "로그인 비밀번호 평문 (dev 전용, 응답 후 저장은 해시)", example = "test1234!")
    private final String password;

    @Schema(description = "Access Token (Swagger Authorize에 Bearer로 입력)")
    private final String accessToken;

    public TestUserResponseDto(User user, String loginId, String password, String accessToken) {
        this.userId = user.getId();
        this.nickname = user.getNickname();
        this.loginId = loginId;
        this.password = password;
        this.accessToken = accessToken;
    }
}
