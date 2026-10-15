package com.loop.loop_backend.Artist.domain;

import com.loop.loop_backend.Concert.domain.ConcertCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

// 아티스트 관련 상품 규칙:
// - 입력한 순서대로 둔다(그 순서로 공연 상세에 노출). 새 상품은 맨 뒤에 추가한다.
//   수정·삭제·순서 변경은 목록을 통째로 교체하고, 빈 목록이면 모두 비운다.
// - 링크·이미지 주소는 필수이고 http/https만. 상품명은 선택(앞뒤 공백 정리, 빈 값이면 비움), 255자까지.
// - 잘못된 상품이 하나라도 있으면 목록 전체를 반영하지 않는다(기존 목록 유지).
class ArtistProductRulesTest {

    private static final String LINK = "https://www.cdjapan.co.jp/aff/click.cgi/e86NDzbdSLQ/7784/A103176/product%2FPCXP-51237";
    private static final String IMAGE = "https://www.cdjapan.co.jp/image.html?id=PCXP-51237";

    private static Artist artist() {
        return Artist.builder().name("Official髭男dism").category(ConcertCategory.J_POP_ARTIST).build();
    }

    private static void assertRejected(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 상품은_입력한_순서대로_저장되고_상품명과_주소의_공백을_정리한다() {
        Artist artist = artist();

        artist.replaceProducts(List.of(
                new ArtistProduct(" Rejoice ", " " + LINK + " ", IMAGE),
                new ArtistProduct(null, "https://www.cdjapan.co.jp/aff/2", "https://www.cdjapan.co.jp/image.html?id=2")));

        assertThat(artist.getProducts())
                .extracting(ArtistProduct::getTitle, ArtistProduct::getLinkUrl, ArtistProduct::getImageUrl)
                .containsExactly(
                        tuple("Rejoice", LINK, IMAGE),
                        tuple(null, "https://www.cdjapan.co.jp/aff/2", "https://www.cdjapan.co.jp/image.html?id=2"));
    }

    @Test
    void 상품명은_비워도_된다() {
        Artist artist = artist();

        artist.replaceProducts(List.of(new ArtistProduct("  ", LINK, IMAGE)));

        assertThat(artist.getProducts()).singleElement().satisfies(p -> assertThat(p.getTitle()).isNull());
    }

    @Test
    void 상품명은_255자까지_받는다() {
        Artist artist = artist();

        artist.replaceProducts(List.of(new ArtistProduct("가".repeat(255), LINK, IMAGE)));
        assertRejected(() -> artist.replaceProducts(List.of(new ArtistProduct("가".repeat(256), LINK, IMAGE))));
    }

    @Test
    void 상품_링크와_이미지_주소는_필수다() {
        Artist artist = artist();

        assertRejected(() -> artist.replaceProducts(List.of(new ArtistProduct("상품", null, IMAGE))));
        assertRejected(() -> artist.replaceProducts(List.of(new ArtistProduct("상품", LINK, " "))));
    }

    @Test
    void 상품_링크와_이미지는_http_https_주소만_받는다() {
        Artist artist = artist();

        assertRejected(() -> artist.replaceProducts(List.of(new ArtistProduct("상품", "javascript:alert(1)", IMAGE))));
        assertRejected(() -> artist.replaceProducts(List.of(new ArtistProduct("상품", LINK, "cdjapan.co.jp/image.html?id=1"))));
    }

    @Test
    void 잘못된_상품이_하나라도_있으면_기존_상품은_그대로다() {
        Artist artist = artist();
        artist.replaceProducts(List.of(new ArtistProduct("기존", LINK, IMAGE)));

        assertRejected(() -> artist.replaceProducts(List.of(
                new ArtistProduct("새 상품", LINK, IMAGE),
                new ArtistProduct("잘못된 상품", null, IMAGE))));

        assertThat(artist.getProducts()).extracting(ArtistProduct::getTitle).containsExactly("기존");
    }

    @Test
    void 추가한_상품은_맨_뒤에_붙고_같은_규칙으로_정리된다() {
        Artist artist = artist();
        artist.replaceProducts(List.of(new ArtistProduct("기존", LINK, IMAGE)));

        artist.addProduct(new ArtistProduct(" 새 상품 ", " https://www.cdjapan.co.jp/aff/2 ", "https://www.cdjapan.co.jp/image.html?id=2"));

        assertThat(artist.getProducts())
                .extracting(ArtistProduct::getTitle, ArtistProduct::getLinkUrl)
                .containsExactly(tuple("기존", LINK), tuple("새 상품", "https://www.cdjapan.co.jp/aff/2"));
    }

    @Test
    void 잘못된_상품은_추가되지_않는다() {
        Artist artist = artist();
        artist.replaceProducts(List.of(new ArtistProduct("기존", LINK, IMAGE)));

        assertRejected(() -> artist.addProduct(new ArtistProduct("상품", "javascript:alert(1)", IMAGE)));
        assertRejected(() -> artist.addProduct(new ArtistProduct("상품", LINK, null)));

        assertThat(artist.getProducts()).extracting(ArtistProduct::getTitle).containsExactly("기존");
    }

    @Test
    void 빈_목록을_보내면_상품을_모두_비운다() {
        Artist artist = artist();
        artist.replaceProducts(List.of(new ArtistProduct("기존", LINK, IMAGE)));

        artist.replaceProducts(List.of());

        assertThat(artist.getProducts()).isEmpty();
    }
}