package com.loop.loop_backend.Artist.domain;

import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.common.util.WebUrls;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "artists")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Artist {

    private static final int PRODUCT_TITLE_MAX_LENGTH = 255; // artist_products.title 길이

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "base_name", length = 100)
    private String baseName;

    @Column(name = "name_ko", length = 200)
    private String nameKo;

    @Column(name = "name_alias", length = 200)
    private String nameAlias;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "auto_fetch_concerts", nullable = false)
    private boolean autoFetchConcerts;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30, nullable = false)
    private ConcertCategory category;

    // iTunes 연결 정보 (AD-03). 연결된 아티스트만 곡 불러오기 가능
    @Column(name = "itunes_artist_id")
    private Long itunesArtistId;

    @Column(name = "artist_view_url", length = 500)
    private String artistViewUrl;

    // 공식 SNS·홈페이지 (관리자 수기 입력). 공연 상세에 노출. http/https 주소만.
    @Column(name = "instagram_url", length = 500)
    private String instagramUrl;

    @Column(name = "x_url", length = 500)
    private String xUrl;

    @Column(name = "homepage_url", length = 500)
    private String homepageUrl;

    /** 관련 상품(CDJapan 어필리에이트 등). 입력한 순서대로 이 아티스트의 공연 상세에 보인다. 0개면 상품 섹션을 그리지 않는다. */
    @ElementCollection
    @CollectionTable(name = "artist_products", joinColumns = @JoinColumn(name = "artist_id"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    @OrderColumn(name = "display_order")
    @Builder.Default
    private List<ArtistProduct> products = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (category == null) category = ConcertCategory.J_POP_ARTIST;
    }

    public void update(String name, String baseName, String nameKo, String nameAlias,
                       String imageUrl, ConcertCategory category) {
        this.name = name;
        this.baseName = baseName;
        this.nameKo = nameKo;
        this.nameAlias = nameAlias;
        this.imageUrl = imageUrl;
        if (category != null) this.category = category;
    }

    public void updateAutoFetch(boolean autoFetchConcerts) {
        this.autoFetchConcerts = autoFetchConcerts;
    }

    /** 공식 링크 전체 교체. 빈 값은 비우고, http/https 주소가 아니면 400. */
    public void changeLinks(String instagramUrl, String xUrl, String homepageUrl) {
        this.instagramUrl = WebUrls.normalize(instagramUrl);
        this.xUrl = WebUrls.normalize(xUrl);
        this.homepageUrl = WebUrls.normalize(homepageUrl);
    }

    /**
     * 상품 목록 통째로 교체(입력 순서 유지). 링크·이미지 주소는 필수, http/https 주소만(아니면 400).
     * 상품명은 앞뒤 공백을 지우고 빈 값이면 비운다. 하나라도 잘못되면 기존 목록을 그대로 둔다.
     */
    public void replaceProducts(List<ArtistProduct> products) {
        List<ArtistProduct> cleaned = new ArrayList<>();
        for (ArtistProduct p : products == null ? List.<ArtistProduct>of() : products) {
            cleaned.add(cleanProduct(p));
        }
        this.products.clear();
        this.products.addAll(cleaned);
    }

    /** 상품 하나를 맨 뒤에 추가한다. 규칙은 replaceProducts와 같다. */
    public void addProduct(ArtistProduct product) {
        this.products.add(cleanProduct(product));
    }

    private static ArtistProduct cleanProduct(ArtistProduct p) {
        String linkUrl = WebUrls.normalize(p.getLinkUrl());
        String imageUrl = WebUrls.normalize(p.getImageUrl());
        if (linkUrl == null || imageUrl == null) {
            throw new IllegalArgumentException("상품 링크와 이미지 주소는 비울 수 없다");
        }
        String title = (p.getTitle() == null || p.getTitle().isBlank()) ? null : p.getTitle().trim();
        if (title != null && title.length() > PRODUCT_TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("상품명은 " + PRODUCT_TITLE_MAX_LENGTH + "자까지 입력할 수 있다");
        }
        return new ArtistProduct(title, linkUrl, imageUrl);
    }

    public void linkItunes(Long itunesArtistId, String artistViewUrl) {
        this.itunesArtistId = itunesArtistId;
        this.artistViewUrl = artistViewUrl;
    }
}
