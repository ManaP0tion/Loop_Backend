package com.loop.loop_backend.Concert.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공연 관련 상품 1건(CDJapan 어필리에이트 등). 관리자가 링크·이미지 주소를 그대로 붙여 넣고, 사용자 화면에도 그대로 내려간다.
 * 상품 코드로 주소를 조합하지 않는다 - 입력이 단순하고 CDJapan이 아닌 상품도 넣을 수 있다(대신 어필리에이트 계정이 바뀌면 다시 입력).
 * 상품명·종류(CD, Blu-ray 등)는 표시용 자유 문자열이라 비어 있을 수 있다. 입력 규칙은 Concert.replaceProducts가 검사한다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ConcertProduct {

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "type", length = 50)
    private String type;

    @Column(name = "link_url", length = 500, nullable = false)
    private String linkUrl;

    @Column(name = "image_url", length = 500, nullable = false)
    private String imageUrl;
}