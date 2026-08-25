package com.loop.loop_backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "관리자 2단계 인증 요구 응답 DTO")
public class TwoFactorChallengeResponseDto {

    @Schema(description = "2단계 인증 필요 여부 (항상 true)")
    private final boolean twoFactorRequired = true;

    @Schema(description = "코드 검증 시 함께 보낼 challenge 식별자")
    private final String challengeId;

    public TwoFactorChallengeResponseDto(String challengeId) {
        this.challengeId = challengeId;
    }
}
