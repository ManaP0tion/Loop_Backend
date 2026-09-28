package com.loop.loop_backend.Concert.dto;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// 스프링 컨텍스트 없이, 스웨거가 실제로 만드는 OpenAPI 스키마에 nullable/required가 반영되는지 확인한다.
// (이 프로젝트는 컨텍스트 테스트가 MAIL_HOST 환경변수 문제로 안 돌아서, 이 방식으로 검증한다)
class ConcertDetailDtoSchemaTest {

    private Schema<?> resolve(Class<?> dtoClass) {
        ResolvedSchema resolved = ModelConverters.getInstance().resolveAsResolvedSchema(
                new io.swagger.v3.core.converter.AnnotatedType(dtoClass));
        return resolved.schema;
    }

    @Test
    void upcomingDetailDto_필수_필드는_required_목록에_있고_nullable이_아니다() {
        Schema<?> schema = resolve(ConcertUpcomingDetailDto.class);

        assertThat(schema.getRequired()).contains("concertId", "title");
        assertThat(schema.getProperties().get("concertId").getNullable()).isNotEqualTo(Boolean.TRUE);
        assertThat(schema.getProperties().get("title").getNullable()).isNotEqualTo(Boolean.TRUE);
    }

    @Test
    void upcomingDetailDto_null_가능한_필드는_required가_아니고_nullable이다() {
        Schema<?> schema = resolve(ConcertUpcomingDetailDto.class);
        Set<String> required = schema.getRequired() == null ? Set.of() : Set.copyOf(schema.getRequired());

        for (String field : new String[]{
                // dDay는 Lombok getDDay() -> Jackson이 "dday"(소문자)로 직렬화한다. 실제 응답 필드명도 dday다.
                "artistId", "artistName", "venue", "startDate", "endDate", "dday", "showtime",
                "presaleAvailable", "presaleDate", "generalSaleDate", "ticketVendors",
                "venueAddress", "venueCapacity", "venueLatitude", "venueLongitude", "seatingChartImageUrl"
        }) {
            assertThat(required).as("required 목록에 %s가 없어야 함", field).doesNotContain(field);
            assertThat(schema.getProperties().get(field).getNullable())
                    .as("%s는 nullable=true여야 함", field).isTrue();
        }
    }

    @Test
    void pastDetailDto_필수_필드는_required_목록에_있고_nullable이_아니다() {
        Schema<?> schema = resolve(ConcertPastDetailDto.class);

        assertThat(schema.getRequired()).contains("concertId", "title");
        assertThat(schema.getProperties().get("concertId").getNullable()).isNotEqualTo(Boolean.TRUE);
        assertThat(schema.getProperties().get("title").getNullable()).isNotEqualTo(Boolean.TRUE);
    }

    @Test
    void pastDetailDto_null_가능한_필드는_required가_아니고_nullable이다() {
        Schema<?> schema = resolve(ConcertPastDetailDto.class);
        Set<String> required = schema.getRequired() == null ? Set.of() : Set.copyOf(schema.getRequired());

        for (String field : new String[]{"artistName", "venue", "startDate", "endDate", "showtime"}) {
            assertThat(required).as("required 목록에 %s가 없어야 함", field).doesNotContain(field);
            assertThat(schema.getProperties().get(field).getNullable())
                    .as("%s는 nullable=true여야 함", field).isTrue();
        }
    }
}