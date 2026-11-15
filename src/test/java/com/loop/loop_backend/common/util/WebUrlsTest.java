package com.loop.loop_backend.common.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 관리자가 직접 입력하는 외부 링크(공연 특설 공식 사이트, 아티스트 인스타·X·홈페이지) 규칙:
// - 앱이 그대로 여는 값이라 http/https로 시작하는 전체 주소만 저장한다(javascript:, 스킴 없는 주소, @아이디는 400).
// - 일본어 도메인처럼 실제로 열리는 주소는 막지 않는다.
// - 빈 값은 비움(null), 앞뒤 공백은 지운다.
// - 500자를 넘으면 400.
class WebUrlsTest {

    @Test
    void http_https_주소는_그대로_저장된다() {
        for (String url : List.of("https://yuuri.jp", "http://yuuri.jp", "HTTPS://YUURI.JP",
                "https://www.instagram.com/yuuri_official?igsh=abc", "https://x.com/yuuri_official")) {
            assertThat(WebUrls.normalize(url)).as(url).isEqualTo(url);
        }
    }

    @Test
    void 일본어_도메인이나_일본어_경로도_받는다() {
        assertThat(WebUrls.normalize("https://例え.jp")).isEqualTo("https://例え.jp");
        assertThat(WebUrls.normalize("https://ja.wikipedia.org/wiki/優里")).isEqualTo("https://ja.wikipedia.org/wiki/優里");
    }

    @Test
    void 앞뒤_공백은_지운다() {
        assertThat(WebUrls.normalize("  https://yuuri.jp  ")).isEqualTo("https://yuuri.jp");
    }

    @Test
    void 빈_값은_비움이다() {
        assertThat(WebUrls.normalize(null)).isNull();
        assertThat(WebUrls.normalize("")).isNull();
        assertThat(WebUrls.normalize("   ")).isNull();
    }

    @Test
    void http_https_주소가_아니면_거절한다() {
        for (String invalid : List.of("javascript:alert(1)", "JavaScript://x.com/%0Aalert(1)", "instagram.com/yuuri_official",
                "@yuuri_official", "ftp://yuuri.jp", "https://", "https:///path", "https://yuuri .jp")) {
            assertThatThrownBy(() -> WebUrls.normalize(invalid))
                    .as(invalid)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void 오백자까지_받고_넘으면_거절한다() {
        String prefix = "https://yuuri.jp/";
        String max = prefix + "a".repeat(500 - prefix.length());

        assertThat(WebUrls.normalize(max)).isEqualTo(max);
        assertThatThrownBy(() -> WebUrls.normalize(max + "a")).isInstanceOf(IllegalArgumentException.class);
    }
}