package com.loop.loop_backend.Artist.dto;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.domain.ArtistProduct;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 아티스트 관련 상품 1건. 공연 상세(사용자)와 어드민 아티스트 상품 조회에 같은 형태로 내려가고,
 * 어드민 상품 목록 저장(수정·삭제·순서 변경)도 이 형태로 받는다.
 */
@Schema(description = "관련 상품(CDJapan 어필리에이트 등). 링크·이미지는 저장된 주소 그대로")
public record ArtistProductDto(

        @Schema(description = "상품명. 없으면 null", example = "Official HIGE DANdism Arena Tour 2024 - Rejoice -",
                types = {"string", "null"})
        String title,

        @Schema(description = "상품 링크",
                example = "https://www.cdjapan.co.jp/aff/click.cgi/e86NDzbdSLQ/7784/A103176/product%2FPCXP-51237",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String linkUrl,

        @Schema(description = "상품 이미지 주소", example = "https://www.cdjapan.co.jp/image.html?id=PCXP-51237",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String imageUrl
) {

    public ArtistProduct toDomain() {
        return new ArtistProduct(title, linkUrl, imageUrl);
    }

    public static List<ArtistProduct> toDomain(List<ArtistProductDto> products) {
        return products.stream().map(ArtistProductDto::toDomain).toList();
    }

    /** 아티스트가 없는 공연(페스티벌 등)은 빈 목록. */
    public static List<ArtistProductDto> listOf(Artist artist) {
        if (artist == null) return List.of();
        return artist.getProducts().stream()
                .map(p -> new ArtistProductDto(p.getTitle(), p.getLinkUrl(), p.getImageUrl()))
                .toList();
    }
}