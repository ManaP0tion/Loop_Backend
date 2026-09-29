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
class ConcertDetailDtoSchemaTest {

    private Schema<?> resolve(Class<?> dtoClass) {
        ModelResolver resolver = new ModelResolver(Json31.mapper()).openapi31(true);
        return new ModelConverterContextImpl(resolver).resolve(new AnnotatedType(dtoClass));
    }

    // "승인 시 필수값 검증"이 생기면 실제로도 이렇게 된다는 전제의 최종 계약이다.
    // 그 검증 기능은 아직 없어서 지금 실제 응답은 이 필드들도 null일 수 있다 - 클래스 상단 주석 참고.
    private static final String[] UPCOMING_NON_NULL_FIELDS = {
            "artistId", "artistName", "posterUrl", "venue", "startDate", "endDate",
            // dday는 Lombok getDDay() -> Jackson이 "dday"(소문자)로 직렬화한다. 실제 응답 필드명도 dday다.
            "dday", "showtime", "presaleAvailable",
            "venueAddress", "venueCapacity", "venueLatitude", "venueLongitude", "seatingChartImageUrl"
    };

    // 예매처가 아직 안 정해졌거나 선예매/일반예매 날짜가 미정인 공연은 계속 있을 수 있어 null 허용.
    private static final String[] UPCOMING_NULLABLE_FIELDS = {"presaleDate", "generalSaleDate", "ticketVendors"};

    @Test
    void upcomingDetailDto_필수_필드와_non_null_필드는_required_목록에_있고_null_타입이_없다() {
        Schema<?> schema = resolve(ConcertUpcomingDetailDto.class);
        Set<String> required = schema.getRequired() == null ? Set.of() : Set.copyOf(schema.getRequired());

        assertThat(required).contains("concertId", "title");
        assertThat(typesOf(schema, "concertId")).doesNotContain("null");
        assertThat(typesOf(schema, "title")).doesNotContain("null");

        for (String field : UPCOMING_NON_NULL_FIELDS) {
            assertThat(typesOf(schema, field)).as("%s의 type에 null이 없어야 함", field).doesNotContain("null");
        }
    }

    @Test
    void upcomingDetailDto_예매처와_예매날짜만_null_가능하다() {
        Schema<?> schema = resolve(ConcertUpcomingDetailDto.class);
        Set<String> required = schema.getRequired() == null ? Set.of() : Set.copyOf(schema.getRequired());

        for (String field : UPCOMING_NULLABLE_FIELDS) {
            assertThat(required).as("required 목록에 %s가 없어야 함", field).doesNotContain(field);
            assertThat(typesOf(schema, field)).as("%s의 type에 null이 포함돼야 함", field).contains("null");
        }
    }

    // ConcertUpcomingDetailDto와 같은 정책: "승인 시 필수값 검증"을 전제로 한 최종 계약이라 전부 non-null.
    @Test
    void pastDetailDto_모든_필드는_required_목록에_있고_null_타입이_없다() {
        Schema<?> schema = resolve(ConcertPastDetailDto.class);
        Set<String> required = schema.getRequired() == null ? Set.of() : Set.copyOf(schema.getRequired());

        assertThat(required).contains("concertId", "title");
        for (String field : new String[]{"concertId", "title", "artistName", "venue", "startDate", "endDate", "showtime"}) {
            assertThat(typesOf(schema, field)).as("%s의 type에 null이 없어야 함", field).doesNotContain("null");
        }
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