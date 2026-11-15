package com.loop.loop_backend.Artist.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 아티스트 관련 상품 1건(CDJapan 어필리에이트 등). 그 아티스트의 공연 상세에 그대로 내려간다.
 * 처음 추가할 때는 관리자가 어필리에이트 HTML 전문을 붙여 넣고 서버가 링크·이미지·상품명을 꺼낸다(ArtistProductRequest).
 * 그 뒤 수정·삭제·순서 변경은 목록을 통째로 저장한다. 상품명은 표시용이라 비어 있을 수 있다.
 * 입력 규칙은 Artist.addProduct / replaceProducts가 검사한다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ArtistProduct {

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "link_url", length = 500, nullable = false)
    private String linkUrl;

    @Column(name = "image_url", length = 500, nullable = false)
    private String imageUrl;
}