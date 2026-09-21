package com.loop.loop_backend.Config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Configuration
public class SwaggerConfig {

    private static final String ADMIN_PATH_PREFIX = "/api/admin/";
    private static final String ADMIN_ETC_TAG = "Admin Etc";

    // /api/admin/ 다음 첫 경로 조각 → 스웨거 태그. 새 admin 경로가 생기면 여기에 추가 (없으면 Admin Etc로 모임).
    private static final Map<String, String> ADMIN_TAG_BY_SEGMENT = Map.ofEntries(
            Map.entry("concerts", "Admin Concert"),
            Map.entry("concert-imports", "Admin Concert"),
            Map.entry("artists", "Admin Artist"),
            Map.entry("users", "Admin User"),
            Map.entry("admins", "Admin User"),
            Map.entry("reports", "Admin Report"),
            Map.entry("inquiries", "Admin Inquiry"),
            Map.entry("companion-posts", "Admin Companion"),
            Map.entry("dashboard", "Admin System"),
            Map.entry("access-logs", "Admin System"),
            Map.entry("mail-logs", "Admin System"));

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .displayName("Public API")
                .pathsToMatch("/api/**")
                .pathsToExclude("/api/admin/**")
                .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .displayName("Admin API")
                .pathsToMatch("/api/admin/**")
                .addOpenApiCustomizer(SwaggerConfig::groupAdminTags)
                .build();
    }

    // AdminController 하나가 @Tag("Admin")으로 모든 admin API를 갖고 있어서, 경로 기준으로 태그를 다시 붙인다.
    static void groupAdminTags(OpenAPI openApi) {
        if (openApi.getPaths() == null) return;
        Set<String> usedTags = new TreeSet<>();
        openApi.getPaths().forEach((path, item) -> {
            String tag = adminTagFor(path);
            item.readOperations().forEach(operation -> operation.setTags(List.of(tag)));
            usedTags.add(tag);
        });
        // 클래스 레벨 "Admin" 태그 정의가 남아 빈 섹션으로 보이지 않도록 실제 쓰는 태그만 남긴다.
        openApi.setTags(usedTags.stream()
                .map(name -> new Tag().name(name))
                .collect(Collectors.toCollection(ArrayList::new)));
    }

    static String adminTagFor(String path) {
        if (!path.startsWith(ADMIN_PATH_PREFIX)) return ADMIN_ETC_TAG;
        String segment = path.substring(ADMIN_PATH_PREFIX.length()).split("/")[0];
        return ADMIN_TAG_BY_SEGMENT.getOrDefault(segment, ADMIN_ETC_TAG);
    }

    @Bean
    public OpenAPI openAPI() {
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization");

        return new OpenAPI()
                .info(new Info()
                        // devtemp 전용 임시 하드코딩 - 개발서버/운영서버 스웨거 구분용. dev 병합 시 "Loop API"로 되돌릴 것.
                        .title("Loop API (개발서버)")
                        .description("Loop 백엔드 API 문서")
                        .version("v1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", bearerScheme));
    }
}