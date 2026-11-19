package com.loop.loop_backend.AiJob.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 관리자 처리 결과(피드백) 약속 기준.
 * - items의 항목마다 key와 outcome이 있어야 한다. finalValue는 선택.
 * - 항목 키는 어드민 화면의 행 식별자라 형식을 정하지 않는다. 한 요청에 같은 키는 한 번만.
 * - 처리 결과는 정해진 다섯 가지뿐이다.
 * - 빈 목록도 정상(저장했지만 AI가 채운 항목이 없었음).
 */
class AiJobFeedbackDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper mapper = new ObjectMapper();

    private boolean valid(String json) throws Exception {
        return validator.validate(mapper.readValue(json, AdminAiJobFeedbackRequest.class)).isEmpty();
    }

    @Test
    void 항목_키와_처리_결과가_있으면_받는다() throws Exception {
        assertThat(valid("""
                {"items":[{"key":"generalSales.0.opens","outcome":"APPLIED"},
                          {"key":"showtime.2026-12-05","outcome":"EDITED","finalValue":"18:00"},
                          {"key":"d1-0","outcome":"NOT_APPLIED"},
                          {"key":"songs.3","outcome":"KEPT_EXISTING"},
                          {"key":"link.xUrl","outcome":"MATCHED"}]}""")).isTrue();
    }

    @Test
    void 빈_목록도_받는다() throws Exception {
        assertThat(valid("""
                {"items":[]}""")).isTrue();
    }

    @Test
    void 항목_목록이_없으면_형식_오류다() throws Exception {
        assertThat(valid("{}")).isFalse();
    }

    @Test
    void 항목_키나_처리_결과가_없으면_형식_오류다() throws Exception {
        assertThat(valid("""
                {"items":[{"outcome":"APPLIED"}]}""")).isFalse();
        assertThat(valid("""
                {"items":[{"key":"  ","outcome":"APPLIED"}]}""")).isFalse();
        assertThat(valid("""
                {"items":[{"key":"generalSales.0.opens"}]}""")).isFalse();
    }

    @Test
    void 항목_키는_100자를_넘을_수_없다() throws Exception {
        assertThat(valid("{\"items\":[{\"key\":\"" + "k".repeat(100) + "\",\"outcome\":\"APPLIED\"}]}")).isTrue();
        assertThat(valid("{\"items\":[{\"key\":\"" + "k".repeat(101) + "\",\"outcome\":\"APPLIED\"}]}")).isFalse();
    }

    @Test
    void 한_요청에_같은_항목_키가_두_번_있으면_형식_오류다() throws Exception {
        assertThat(valid("""
                {"items":[{"key":"generalSales.0.opens","outcome":"APPLIED"},
                          {"key":"generalSales.0.opens","outcome":"NOT_APPLIED"}]}""")).isFalse();
    }

    @Test
    void 정해지지_않은_처리_결과는_받지_않는다() {
        assertThatThrownBy(() -> mapper.readValue("""
                {"items":[{"key":"generalSales.0.opens","outcome":"IGNORED"}]}""", AdminAiJobFeedbackRequest.class))
                .isInstanceOf(InvalidFormatException.class);
    }
}
