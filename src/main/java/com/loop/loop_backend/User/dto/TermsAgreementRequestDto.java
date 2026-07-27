package com.loop.loop_backend.User.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "약관 동의 요청 DTO")
public class TermsAgreementRequestDto {

    @Schema(description = "만 19세 이상입니다 (필수)", example = "true")
    private boolean age19Agreed;

    @Schema(description = "루프 이용약관 동의 (필수)", example = "true")
    private boolean termsAgreed;

    @Schema(description = "개인정보 수집동의 (필수)", example = "true")
    private boolean privacyAgreed;

    @Schema(description = "프로필 정보 수집동의 (선택)", example = "true")
    private boolean profileInfoAgreed;
}