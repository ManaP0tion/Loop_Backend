package com.loop.loop_backend.Concert.dto;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverterContextImpl;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// 스프링 컨텍스트 없이, 스웨거가 실제로 만드는 OpenAPI 스키마에 required/null 표기가 반영되는지 확인한다.
// (이 프로젝트는 컨텍스트 테스트가 MAIL_HOST 환경변수 문제로 안 돌아서, 이 방식으로 검증한다)
//
// null 표기는 3.0의 nullable=true 대신 3.1 방식(types에 "null" 포함)을 쓴다 - 스웨거 UI가 nullable=true는
// 타입 옆에 안 보여줘서, 콘서트 상세처럼 null이 많은 응답은 type 배열로 명확히 보이게 하기로 했다.
// ModelConverters.getInstance()의 기본 ModelResolver는 openapi31=false라서 @Schema(types=...)를 줘도
// nullable로 접혀버린다 - 이 프로젝트가 실제로 쓰는 openapi31=true 모드로 직접 리졸버를 만들어야
// 우리가 스웨거에서 실제로 보게 될 것과 같은 결과가 나온다.
//
// 프론트 계약: 목록 필드는 비어 있으면 null이 아니라 빈 배열이고, 값이 없을 수 있는 필드만 null 가능으로 표기한다.
class ConcertDetailDtoSchemaTest {

    private Schema<?> resolve(Class<?> dtoClass) {
        ModelResolver resolver = new ModelResolver(Json31.mapper()).openapi31(true);
        return new ModelConverterContextImpl(resolver).resolve(new AnnotatedType(dtoClass));
    }

    // ---------- 예정 공연 상세 ----------

    // 항상 값이 있는 필드: 식별자·공연명·스크랩 여부, 그리고 비어 있으면 빈 배열로 내려가는 목록들
    private static final String[] UPCOMING_ALWAYS = {
            "concertId", "title", "artists", "showtimes", "presales", "generalSales", "productCodes", "scrapped"
    };

    // 공연 상태에 따라 없을 수 있는 필드: 포스터·날짜 미정, 공연장 정보 없음, 숙소 미노출
    private static final String[] UPCOMING_NULLABLE = {
            "posterUrl", "venue", "startDate", "endDate", "dday", "lodgingUrl"
    };

    @Test
    void upcomingDetailDto_항상_있는_필드는_required이고_null_타입이_없다() {
        Schema<?> schema = resolve(ConcertUpcomingDetailDto.class);

        for (String field : UPCOMING_ALWAYS) {
            assertThat(requiredOf(schema)).as("required 목록에 %s가 있어야 함", field).contains(field);
            assertThat(typesOf(schema, field)).as("%s의 type에 null이 없어야 함", field).doesNotContain("null");
        }
    }

    @Test
    void upcomingDetailDto_없을_수_있는_필드는_null_타입이_있다() {
        Schema<?> schema = resolve(ConcertUpcomingDetailDto.class);

        for (String field : UPCOMING_NULLABLE) {
            assertThat(requiredOf(schema)).as("required 목록에 %s가 없어야 함", field).doesNotContain(field);
            assertThat(typesOf(schema, field)).as("%s의 type에 null이 포함돼야 함", field).contains("null");
        }
    }

    @Test
    void upcomingDetailDto_옛_필드는_응답에_없다() {
        Schema<?> schema = resolve(ConcertUpcomingDetailDto.class);

        assertThat(schema.getProperties()).doesNotContainKeys(
                "artistId", "artistName", "showtime", "presaleAvailable", "presaleDate", "generalSaleDate",
                "ticketVendors", "venueAddress", "venueCapacity", "venueLatitude", "venueLongitude", "seatingChartImageUrl");
    }

    // ---------- 지난 공연 상세 ----------

    @Test
    void pastDetailDto_항상_있는_필드는_required이고_없을_수_있는_필드는_null_타입이_있다() {
        Schema<?> schema = resolve(ConcertPastDetailDto.class);

        // 지난 공연은 날짜로 판단하므로 시작일이 항상 있다
        for (String field : new String[]{"concertId", "title", "startDate", "showtimes", "scrapped"}) {
            assertThat(requiredOf(schema)).as("required 목록에 %s가 있어야 함", field).contains(field);
            assertThat(typesOf(schema, field)).as("%s의 type에 null이 없어야 함", field).doesNotContain("null");
        }
        for (String field : new String[]{"artistName", "venue", "endDate"}) {
            assertThat(typesOf(schema, field)).as("%s의 type에 null이 포함돼야 함", field).contains("null");
        }
    }

    // ---------- 공연장 ----------

    @Test
    void 공연장은_이름만_항상_있고_나머지는_null일_수_있다() {
        Schema<?> schema = resolve(ConcertVenueDto.class);

        assertThat(requiredOf(schema)).contains("name");
        for (String field : new String[]{"address", "capacity", "seatViewUrl", "kakaoMapUrl", "naverMapUrl", "latitude", "longitude"}) {
            assertThat(typesOf(schema, field)).as("%s의 type에 null이 포함돼야 함", field).contains("null");
        }
    }

    private static Set<String> requiredOf(Schema<?> schema) {
        return schema.getRequired() == null ? Set.of() : Set.copyOf(schema.getRequired());
    }

    // OAS 3.1은 nullable 대신 type을 집합으로 표현한다(예: {"string","null"}). 단일 타입이면 getType()에,
    // 여러 타입이면 getTypes()에 들어가서 둘 다 합쳐서 본다.
    private Set<String> typesOf(Schema<?> parent, String field) {
        Schema<?> property = parent.getProperties().get(field);
        Set<String> types = new java.util.HashSet<>();
        if (property.getTypes() != null) types.addAll(property.getTypes());
        if (property.getType() != null) types.add(property.getType());
        return types;
    }
}