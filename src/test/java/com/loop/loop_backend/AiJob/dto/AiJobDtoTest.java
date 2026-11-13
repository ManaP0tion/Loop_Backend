package com.loop.loop_backend.AiJob.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.loop_backend.AiJob.dto.payload.ConcertEnrichPayload;
import com.loop.loop_backend.AiJob.dto.payload.FestivalLineupPayload;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 작업 연동 약속 기준.
 * - 결과 보고(3절): DONE이면 result, FAILED면 error(code·message·retryable)가 있어야 한다. 상태는 DONE·FAILED뿐이다.
 *   LEASE_EXPIRED는 Backend만 붙이는 코드라 워커가 보고할 수 없다.
 * - claim(3절): Backend가 모르는 종류가 섞여도 요청 자체는 받는다.
 * - payload(2-1절): 날짜는 yyyy-MM-dd 문자열만, 목록 필드는 null 불가([]).
 */
class AiJobDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private boolean validResult(String json) throws Exception {
        return validator.validate(mapper.readValue(json, AiJobResultRequest.class)).isEmpty();
    }

    @Nested
    class 결과_보고_형식 {

        @Test
        void 결과가_있는_DONE은_받는다() throws Exception {
            assertThat(validResult("""
                    {"workerId":"pm-macbook","attempt":1,"status":"DONE",
                     "result":{"status":"PARTIAL","days":[],"dayUnknown":[]}}""")).isTrue();
        }

        @Test
        void 찾아봤는데_없다는_NOT_FOUND_결과도_정상_DONE이다() throws Exception {
            assertThat(validResult("""
                    {"workerId":"pm-macbook","attempt":1,"status":"DONE","result":{"status":"NOT_FOUND"}}""")).isTrue();
        }

        @Test
        void 결과가_없거나_null인_DONE은_형식_오류다() throws Exception {
            assertThat(validResult("""
                    {"workerId":"pm-macbook","attempt":1,"status":"DONE"}""")).isFalse();
            assertThat(validResult("""
                    {"workerId":"pm-macbook","attempt":1,"status":"DONE","result":null}""")).isFalse();
        }

        @Test
        void 실패_정보가_있는_FAILED는_받는다() throws Exception {
            assertThat(validResult("""
                    {"workerId":"pm-macbook","attempt":1,"status":"FAILED",
                     "error":{"code":"LLM_TIMEOUT","message":"AI 분석 시간이 초과됐습니다.","retryable":true}}""")).isTrue();
        }

        @Test
        void 실패_정보가_없는_FAILED는_형식_오류다() throws Exception {
            assertThat(validResult("""
                    {"workerId":"pm-macbook","attempt":1,"status":"FAILED"}""")).isFalse();
        }

        @Test
        void 실패_정보에_코드_문구_다시_시도_여부_중_하나라도_없으면_형식_오류다() throws Exception {
            assertThat(validResult("""
                    {"workerId":"pm-macbook","attempt":1,"status":"FAILED",
                     "error":{"message":"실패","retryable":true}}""")).isFalse();
            assertThat(validResult("""
                    {"workerId":"pm-macbook","attempt":1,"status":"FAILED",
                     "error":{"code":"LLM_FAILED","retryable":true}}""")).isFalse();
            assertThat(validResult("""
                    {"workerId":"pm-macbook","attempt":1,"status":"FAILED",
                     "error":{"code":"LLM_FAILED","message":"실패"}}""")).isFalse();
        }

        @Test
        void 워커는_LEASE_EXPIRED_코드를_보고할_수_없다() throws Exception {
            assertThat(validResult("""
                    {"workerId":"pm-macbook","attempt":1,"status":"FAILED",
                     "error":{"code":"LEASE_EXPIRED","message":"시간 초과","retryable":true}}""")).isFalse();
        }

        @Test
        void 워커_식별자와_시도_번호는_필수다() throws Exception {
            assertThat(validResult("""
                    {"attempt":1,"status":"DONE","result":{}}""")).isFalse();
            assertThat(validResult("""
                    {"workerId":"pm-macbook","status":"DONE","result":{}}""")).isFalse();
        }

        @Test
        void DONE_FAILED_외의_상태는_받지_않는다() {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> mapper.readValue("""
                    {"workerId":"pm-macbook","attempt":1,"status":"RUNNING"}""", AiJobResultRequest.class))
                    .isInstanceOf(com.fasterxml.jackson.databind.exc.InvalidFormatException.class);
        }
    }

    @Nested
    class claim_요청_형식 {

        @Test
        void Backend가_모르는_종류가_섞여도_요청은_받는다() throws Exception {
            AiJobClaimRequest request = mapper.readValue("""
                    {"workerId":"pm-macbook","types":["CONCERT_ENRICH","NEW_TYPE_FROM_FUTURE"]}""", AiJobClaimRequest.class);

            assertThat(validator.validate(request)).isEmpty();
        }

        @Test
        void 종류_목록이_없으면_형식_오류다() throws Exception {
            AiJobClaimRequest request = mapper.readValue("""
                    {"workerId":"pm-macbook"}""", AiJobClaimRequest.class);

            assertThat(validator.validate(request)).isNotEmpty();
        }
    }

    @Nested
    class payload_형식 {

        @Test
        void 날짜는_yyyy_MM_dd_문자열로_나간다() {
            FestivalLineupPayload payload = new FestivalLineupPayload(
                    200L, "WONDERLIVET 2026", null, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 22), null);

            JsonNode json = mapper.valueToTree(payload);

            assertThat(json.get("startDate").isTextual()).isTrue();
            assertThat(json.get("startDate").asText()).isEqualTo("2026-11-20");
            assertThat(json.get("endDate").asText()).isEqualTo("2026-11-22");
        }

        @Test
        void 공연_정보_payload의_목록_필드는_값이_없어도_null이_아닌_빈_목록으로_나간다() {
            ConcertEnrichPayload payload = new ConcertEnrichPayload(
                    31L, null, "YUURI LIVE 2026 IN SEOUL", null, ConcertCategory.STAGE, null,
                    null, null, null, null);

            JsonNode json = mapper.valueToTree(payload);

            assertThat(json.get("titleAliases").isArray()).isTrue();
            assertThat(json.get("titleAliases")).isEmpty();
            assertThat(json.get("artists").isArray()).isTrue();
            assertThat(json.get("artists")).isEmpty();
            assertThat(json.get("knownVendors").isArray()).isTrue();
            assertThat(json.get("knownVendors")).isEmpty();
        }

        @Test
        void 라인업_payload의_별칭_목록도_값이_없으면_빈_목록으로_나간다() {
            FestivalLineupPayload payload = new FestivalLineupPayload(
                    200L, "WONDERLIVET 2026", null, LocalDate.of(2026, 11, 20), null, null);

            JsonNode json = mapper.valueToTree(payload);

            assertThat(json.get("titleAliases").isArray()).isTrue();
            assertThat(json.get("titleAliases")).isEmpty();
        }

        @Test
        void 공연_분류는_Loop_분류_이름_그대로_나간다() {
            ConcertEnrichPayload payload = new ConcertEnrichPayload(
                    31L, null, "FES", null, ConcertCategory.FESTIVAL, null, null, null, null, null);

            assertThat(mapper.valueToTree(payload).get("category").asText()).isEqualTo("FESTIVAL");
        }
    }
}