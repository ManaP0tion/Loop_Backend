package com.loop.loop_backend.User.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 약관 동의 요청 요구사항:
// - 나이 동의는 만 14세 이상 기준이고 키는 age14Agreed다.
// - 구버전 앱은 예전 키(age19Agreed)로 보낸다 - 그 값도 같은 동의로 받아야 앱 업데이트 전 사용자가 약관 동의에서 막히지 않는다.
class TermsAgreementRequestDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void age14Agreed로_보낸_동의를_받는다() throws Exception {
        TermsAgreementRequestDto dto = objectMapper.readValue(
                "{\"age14Agreed\":true,\"termsAgreed\":true,\"privacyAgreed\":true,\"profileInfoAgreed\":false}",
                TermsAgreementRequestDto.class);

        assertThat(dto.isAge14Agreed()).isTrue();
    }

    @Test
    void 구버전_앱이_예전_키_age19Agreed로_보내도_만_14세_동의로_받는다() throws Exception {
        TermsAgreementRequestDto dto = objectMapper.readValue(
                "{\"age19Agreed\":true,\"termsAgreed\":true,\"privacyAgreed\":true,\"profileInfoAgreed\":false}",
                TermsAgreementRequestDto.class);

        assertThat(dto.isAge14Agreed()).isTrue();
    }
}
