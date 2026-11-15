package com.loop.loop_backend.Config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.tags.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Admin API 그룹 안에서 도메인별 하위 분류(Admin Concert / Admin User / Admin Report ...)로 묶이는지 검증한다.
class SwaggerConfigTest {

    @Test
    void 관리자_경로는_도메인별_태그로_묶인다() {
        assertThat(SwaggerConfig.adminTagFor("/api/admin/concerts/{id}")).isEqualTo("Admin Concert");
        assertThat(SwaggerConfig.adminTagFor("/api/admin/users/{id}/suspend")).isEqualTo("Admin User");
        assertThat(SwaggerConfig.adminTagFor("/api/admin/reports")).isEqualTo("Admin Report");
    }

    // 공연 관리 페이지의 두 목록(선별 대기 / 등록된 공연)을 스웨거에서도 나눠 보이게 한다.
    @Test
    void 선별_대기와_KOPIS_수동_수집은_등록된_공연과_다른_태그로_묶인다() {
        assertThat(SwaggerConfig.adminTagFor("/api/admin/concert-imports")).isEqualTo("Admin Concert Import");
        assertThat(SwaggerConfig.adminTagFor("/api/admin/concert-imports/{id}/approve")).isEqualTo("Admin Concert Import");
        assertThat(SwaggerConfig.adminTagFor("/api/admin/concerts/sync")).isEqualTo("Admin Concert Import");
        assertThat(SwaggerConfig.adminTagFor("/api/admin/concerts")).isEqualTo("Admin Concert");
        assertThat(SwaggerConfig.adminTagFor("/api/admin/concerts/{id}/image")).isEqualTo("Admin Concert");
    }

    @Test
    void 공연_관련_태그에는_어느_화면용인지_설명이_붙는다() {
        OpenAPI openApi = new OpenAPI()
                .paths(new Paths()
                        .addPathItem("/api/admin/concerts/{id}", new PathItem().get(new Operation()))
                        .addPathItem("/api/admin/concert-imports", new PathItem().get(new Operation()))
                        .addPathItem("/api/admin/venues", new PathItem().get(new Operation())));

        SwaggerConfig.groupAdminTags(openApi);

        assertThat(openApi.getTags()).allSatisfy(tag -> assertThat(tag.getDescription()).isNotBlank());
    }

    @Test
    void 곡_관리_API는_artists_아래_경로도_Admin_Song으로_묶인다() {
        assertThat(SwaggerConfig.adminTagFor("/api/admin/artists/{artistId}/songs")).isEqualTo("Admin Song");
        assertThat(SwaggerConfig.adminTagFor("/api/admin/artists/{artistId}/songs/fetch")).isEqualTo("Admin Song");
        assertThat(SwaggerConfig.adminTagFor("/api/admin/artists/{artistId}/songs/csv")).isEqualTo("Admin Song");
        assertThat(SwaggerConfig.adminTagFor("/api/admin/artists/{artistId}/itunes")).isEqualTo("Admin Song");
        assertThat(SwaggerConfig.adminTagFor("/api/admin/itunes/artists")).isEqualTo("Admin Song");
        assertThat(SwaggerConfig.adminTagFor("/api/admin/songs/{songId}")).isEqualTo("Admin Song");
        assertThat(SwaggerConfig.adminTagFor("/api/admin/songs/{songId}/image")).isEqualTo("Admin Song");
        assertThat(SwaggerConfig.adminTagFor("/api/admin/artists/{id}")).isEqualTo("Admin Artist");
        assertThat(SwaggerConfig.adminTagFor("/api/admin/artists/{id}/image")).isEqualTo("Admin Artist");
    }

    @Test
    @DisplayName("셋리스트·라인업 API는 공연 경로 아래에 있어도 각자 태그로 묶이고, 나머지 공연 경로는 Admin Concert로 남는다")
    void adminTagFor_SetlistAndLineupUnderConcerts_UseOwnTags() {
        // when & then - 셋리스트
        assertThat(SwaggerConfig.adminTagFor("/api/admin/concerts/{concertId}/performances/{performanceId}/setlists"))
                .isEqualTo("Admin Setlist");
        assertThat(SwaggerConfig.adminTagFor("/api/admin/concerts/{concertId}/performances/{performanceId}/setlists/previous-actuals"))
                .isEqualTo("Admin Setlist");
        assertThat(SwaggerConfig.adminTagFor("/api/admin/concerts/{concertId}/performances/{performanceId}/setlists/{type}"))
                .isEqualTo("Admin Setlist");
        // 라인업
        assertThat(SwaggerConfig.adminTagFor("/api/admin/concerts/{concertId}/lineup")).isEqualTo("Admin Lineup");
        assertThat(SwaggerConfig.adminTagFor("/api/admin/concerts/{concertId}/lineup/{lineupId}/headliner"))
                .isEqualTo("Admin Lineup");
        // 나머지 공연 경로
        assertThat(SwaggerConfig.adminTagFor("/api/admin/concerts/{id}")).isEqualTo("Admin Concert");
        assertThat(SwaggerConfig.adminTagFor("/api/admin/concerts/{id}/image")).isEqualTo("Admin Concert");
    }

    @Test
    @DisplayName("셋리스트·라인업 태그에도 어느 화면용인지 설명이 붙는다")
    void groupAdminTags_SetlistAndLineupTags_HaveDescriptions() {
        // given
        OpenAPI openApi = new OpenAPI()
                .paths(new Paths()
                        .addPathItem("/api/admin/concerts/{concertId}/performances/{performanceId}/setlists",
                                new PathItem().get(new Operation()))
                        .addPathItem("/api/admin/concerts/{concertId}/lineup", new PathItem().get(new Operation())));

        // when
        SwaggerConfig.groupAdminTags(openApi);

        // then
        assertThat(openApi.getTags()).extracting(Tag::getName).containsExactly("Admin Lineup", "Admin Setlist");
        assertThat(openApi.getTags()).allSatisfy(tag -> assertThat(tag.getDescription()).isNotBlank());
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