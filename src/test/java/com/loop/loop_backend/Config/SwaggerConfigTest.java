package com.loop.loop_backend.Config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.tags.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Admin API 그룹 안에서 도메인별 하위 분류(Admin Concert / Admin User / Admin Report ...)로 묶이는지 검증한다.
class SwaggerConfigTest {

    @Test
    void 관리자_경로는_도메인별_태그로_묶인다() {
        assertThat(SwaggerConfig.adminTagFor("/api/admin/concerts/{id}")).isEqualTo("Admin Concert");
        assertThat(SwaggerConfig.adminTagFor("/api/admin/concert-imports/{id}/approve")).isEqualTo("Admin Concert");
        assertThat(SwaggerConfig.adminTagFor("/api/admin/users/{id}/suspend")).isEqualTo("Admin User");
        assertThat(SwaggerConfig.adminTagFor("/api/admin/reports")).isEqualTo("Admin Report");
    }

    @Test
    void 매핑에_없는_관리자_경로는_Admin_Etc로_모인다() {
        assertThat(SwaggerConfig.adminTagFor("/api/admin/something-new")).isEqualTo("Admin Etc");
    }

    @Test
    void 오퍼레이션_태그를_교체하고_쓰지_않는_태그_정의는_제거한다() {
        OpenAPI openApi = new OpenAPI()
                .paths(new Paths()
                        .addPathItem("/api/admin/users", new PathItem().get(new Operation().addTagsItem("Admin")))
                        .addPathItem("/api/admin/reports/{id}", new PathItem().post(new Operation().addTagsItem("Admin"))))
                .addTagsItem(new Tag().name("Admin"));

        SwaggerConfig.groupAdminTags(openApi);

        assertThat(openApi.getPaths().get("/api/admin/users").getGet().getTags()).containsExactly("Admin User");
        assertThat(openApi.getPaths().get("/api/admin/reports/{id}").getPost().getTags()).containsExactly("Admin Report");
        assertThat(openApi.getTags()).extracting(Tag::getName).containsExactly("Admin Report", "Admin User");
    }
}