package com.loop.loop_backend.Concert.dto;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertProduct;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 공연 관련 상품 1건. 관리자 등록·수정 요청과 공연 상세 응답(사용자·관리자)에 같은 형태로 쓴다. */
@Schema(description = "관련 상품(CDJapan 어필리에이트 등). 링크·이미지는 입력한 주소 그대로")
public record ConcertProductDto(

        @Size(max = 255)
        @Schema(description = "상품명. 없으면 null", example = "ベテルギウス (通常盤)", types = {"string", "null"})
        String title,

        @Size(max = 50)
        @Schema(description = "상품 종류(자유 문자열). 예: CD, Blu-ray. 없으면 null", example = "CD", types = {"string", "null"})
        String type,

        @NotBlank
        @Size(max = 500)
        @Schema(description = "상품 링크(필수). http/https 주소만",
                example = "https://www.cdjapan.co.jp/aff/click.cgi/e86NDzbdSLQ/7784/A103176/product%2FPCXP-51237",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String linkUrl,

        @NotBlank
        @Size(max = 500)
        @Schema(description = "상품 이미지 주소(필수). http/https 주소만",
                example = "https://www.cdjapan.co.jp/image.html?id=PCXP-51237",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String imageUrl
) {

    public ConcertProduct toDomain() {
        return new ConcertProduct(title, type, linkUrl, imageUrl);
    }

    public static List<ConcertProduct> toDomain(List<ConcertProductDto> products) {
        return products.stream().map(ConcertProductDto::toDomain).toList();
    }

    public static List<ConcertProductDto> listOf(Concert concert) {
        return concert.getProducts().stream()
                .map(p -> new ConcertProductDto(p.getTitle(), p.getType(), p.getLinkUrl(), p.getImageUrl()))
                .toList();
    }
}