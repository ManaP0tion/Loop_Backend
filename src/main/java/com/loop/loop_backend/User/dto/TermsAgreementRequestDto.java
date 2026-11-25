package com.loop.loop_backend.User.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "약관 동의 요청 DTO")
public class TermsAgreementRequestDto {

    // 예전 키(age19Agreed)도 받는다 - 구버전 앱이 보낸 값이 false로 읽혀 약관 동의가 막히지 않게. 앱 업데이트가 퍼지면 제거
    @Schema(description = "만 14세 이상입니다 (필수)", example = "true")
    @JsonAlias("age19Agreed")
    private boolean age14Agreed;

    @Schema(description = "루프 이용약관 동의 (필수)", example = "true")
    private boolean termsAgreed;

    @Schema(description = "개인정보 수집동의 (필수)", example = "true")
    private boolean privacyAgreed;

    @Schema(description = "프로필 정보 수집동의 (선택)", example = "true")
    private boolean profileInfoAgreed;
}
