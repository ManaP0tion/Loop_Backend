package com.loop.loop_backend.Artist.dto;

import com.loop.loop_backend.Artist.domain.ArtistProduct;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * 아티스트 상품 추가 요청. 관리자가 CDJapan 어필리에이트 HTML 전문을 붙여 넣으면 서버가 링크·이미지·상품명을 꺼낸다.
 * 추가한 뒤의 수정(상품명·순서·삭제)은 목록(ArtistProductDto)으로 통째로 저장한다 - 그때는 HTML을 받지 않는다.
 * 꺼낸 값은 Artist.addProduct가 직접 입력과 같은 규칙으로 검사한다.
 */
@Schema(description = "아티스트 상품 추가 요청(CDJapan 어필리에이트 HTML 전문)")
public record ArtistProductRequest(

        @Schema(description = "CDJapan 어필리에이트 HTML 전문(" + HTML_MAX_LENGTH + "자까지). " +
                "링크(첫 a href)·이미지(img src)·상품명(이미지 없는 링크 글자, 없으면 img alt)을 꺼낸다",
                example = "<div><div><a href=\"https://www.cdjapan.co.jp/aff/click.cgi/e86NDzbdSLQ/7784/A103176/product%2FPCXP-51237\" target=\"_blank\">" +
                        "<img src=\"https://www.cdjapan.co.jp/image.html?id=PCXP-51237\" alt=\"Official HIGE DANdism Arena Tour 2024 - Rejoice - / Official HIGE DANdism\" border=0 width=\"100\" /></a></div>" +
                        "<div><a href=\"https://www.cdjapan.co.jp/aff/click.cgi/e86NDzbdSLQ/7784/A103176/product%2FPCXP-51237\" target=\"_blank\">Official HIGE DANdism Arena Tour 2024 - Rejoice -</a></div>" +
                        "<div>Official HIGE DANdism</div></div>",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String html
) {

    // 어필리에이트 HTML 한 건은 1,000자 안팎. 엉뚱한 페이지 전체를 붙여 넣은 경우를 막는다.
    static final int HTML_MAX_LENGTH = 5000;

    /**
     * CDJapan 어필리에이트 HTML: 이미지 링크, 상품명 링크, 아티스트명이 차례로 있다.
     * 아티스트명은 쓰지 않는다(어느 아티스트의 상품인지는 이미 안다). 링크나 이미지가 없으면 400.
     * 속성값의 &amp; 같은 HTML 이스케이프는 jsoup이 풀어서 준다.
     */
    public ArtistProduct toDomain() {
        if (html == null || html.isBlank()) {
            throw new IllegalArgumentException("상품 HTML을 입력해야 한다");
        }
        if (html.length() > HTML_MAX_LENGTH) {
            throw new IllegalArgumentException("상품 HTML은 " + HTML_MAX_LENGTH + "자까지 입력할 수 있다");
        }
        Document doc = Jsoup.parseBodyFragment(html);
        Element link = doc.selectFirst("a[href]");
        Element image = doc.selectFirst("img[src]");
        if (link == null || image == null) {
            throw new IllegalArgumentException("상품 HTML에서 링크와 이미지를 찾을 수 없다");
        }
        String title = doc.select("a[href]").stream()
                .filter(a -> a.selectFirst("img") == null)
                .map(Element::text)
                .filter(text -> !text.isBlank())
                .findFirst()
                .orElse(image.attr("alt"));
        return new ArtistProduct(title, link.attr("href"), image.attr("src"));
    }
}