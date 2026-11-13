package com.loop.loop_backend.AiJob.domain;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.PersistentClass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * payload(공연 정보 예시만 325바이트)와 AI 결과(수천 자)는 운영 DB(MySQL)에 잘리지 않고 저장돼야 한다.
 * 테스트 DB(H2)에서는 같은 매핑이 길이 제한 없는 clob이 되어 문제가 드러나지 않으므로,
 * MySQL 방언으로 만들어지는 컬럼 타입을 직접 확인한다(DB 연결 없음).
 */
class AiJobColumnTypeTest {

    private StandardServiceRegistry registry;

    @AfterEach
    void close() {
        if (registry != null) {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private String mysqlColumnType(String property) {
        registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .build();
        Metadata metadata = new MetadataSources(registry).addAnnotatedClass(AiJob.class).buildMetadata();
        PersistentClass entity = metadata.getEntityBinding(AiJob.class.getName());
        Column column = (Column) entity.getProperty(property).getColumns().get(0);
        return column.getSqlType(metadata).toLowerCase();
    }

    @Test
    void payload는_MySQL에서_LONGTEXT로_저장된다() {
        assertThat(mysqlColumnType("payload")).isEqualTo("longtext");
    }

    @Test
    void AI_결과는_MySQL에서_LONGTEXT로_저장된다() {
        assertThat(mysqlColumnType("result")).isEqualTo("longtext");
    }
}