package com.loop.loop_backend.Artist.dto;

import com.loop.loop_backend.Artist.domain.ArtistProduct;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 아티스트 상품 추가 요구사항:
// - 관리자는 CDJapan 어필리에이트 HTML 전문을 붙여 넣는다. 서버가 링크·이미지·상품명을 꺼낸다.
//   링크 = 상품 링크 주소, 이미지 = 이미지 주소, 상품명 = 상품명 링크의 글자(없으면 이미지 설명).
//   HTML 끝의 아티스트명은 쓰지 않는다. 비어 있거나 링크나 이미지가 없는 HTML은 거절한다.
class ArtistProductRequestTest {

    // 관리자가 CDJapan에서 복사해 오는 HTML 그대로
    private static final String CDJAPAN_HTML = "<div><div><a href=\"https://www.cdjapan.co.jp/aff/click.cgi/e86NDzbdSLQ/7784/A103176/product%2FPCXP-51237\" target=\"_blank\">"
            + "<img src=\"https://www.cdjapan.co.jp/image.html?id=PCXP-51237\" alt=\"Official HIGE DANdism Arena Tour 2024 - Rejoice - / Official HIGE DANdism\" border=0 width=\"100\" /></a></div>"
            + "<div><a href=\"https://www.cdjapan.co.jp/aff/click.cgi/e86NDzbdSLQ/7784/A103176/product%2FPCXP-51237\" target=\"_blank\">Official HIGE DANdism Arena Tour 2024 - Rejoice -</a></div>\n"
            + "<div>Official HIGE DANdism</div></div";

    private static ArtistProduct fromHtml(String html) {
        return new ArtistProductRequest(html).toDomain();
    }

    @Test
    void CDJapan_HTML에서_링크_이미지_상품명을_꺼낸다() {
        ArtistProduct product = fromHtml(CDJAPAN_HTML);

        assertThat(product.getLinkUrl())
                .isEqualTo("https://www.cdjapan.co.jp/aff/click.cgi/e86NDzbdSLQ/7784/A103176/product%2FPCXP-51237");
        assertThat(product.getImageUrl()).isEqualTo("https://www.cdjapan.co.jp/image.html?id=PCXP-51237");
        assertThat(product.getTitle()).isEqualTo("Official HIGE DANdism Arena Tour 2024 - Rejoice -");
    }

    @Test
    void 상품명_링크가_없으면_이미지_설명을_상품명으로_쓴다() {
        String imageOnly = "<a href=\"https://www.cdjapan.co.jp/aff/1\"><img src=\"https://www.cdjapan.co.jp/image.html?id=1\" alt=\"Rejoice / Official HIGE DANdism\"></a>";

        assertThat(fromHtml(imageOnly).getTitle()).isEqualTo("Rejoice / Official HIGE DANdism");
    }

    @Test
    void HTML_이스케이프된_주소는_원래_주소로_꺼낸다() {
        String escaped = "<a href=\"https://www.cdjapan.co.jp/aff?a=1&amp;b=2\"><img src=\"https://www.cdjapan.co.jp/image.html?id=1&amp;s=2\"></a>";

        ArtistProduct product = fromHtml(escaped);

        assertThat(product.getLinkUrl()).isEqualTo("https://www.cdjapan.co.jp/aff?a=1&b=2");
        assertThat(product.getImageUrl()).isEqualTo("https://www.cdjapan.co.jp/image.html?id=1&s=2");
    }

    @Test
    void 링크나_이미지가_없는_HTML은_거절한다() {
        assertThatThrownBy(() -> fromHtml("<img src=\"https://www.cdjapan.co.jp/image.html?id=1\">"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fromHtml("<a href=\"https://www.cdjapan.co.jp/aff/1\">상품</a>"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fromHtml("ベテルギウス"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 너무_긴_HTML은_거절한다() {
        String huge = CDJAPAN_HTML + " ".repeat(ArtistProductRequest.HTML_MAX_LENGTH);

        assertThatThrownBy(() -> fromHtml(huge)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void HTML을_비워_보내면_거절한다() {
        assertThatThrownBy(() -> fromHtml(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fromHtml("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}