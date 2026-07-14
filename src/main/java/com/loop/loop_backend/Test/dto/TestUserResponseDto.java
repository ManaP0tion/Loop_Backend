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

    @Schema(description = "발급된 액세스 토큰 (테스트 전용 - 운영 제거 예정)")
    private final String accessToken;

    public TestUserResponseDto(User user, String accessToken) {
        this.userId = user.getId();
        this.nickname = user.getNickname();
        this.accessToken = accessToken;
    }
}
