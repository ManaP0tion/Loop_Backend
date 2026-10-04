package com.loop.loop_backend.Artist.domain;

import com.loop.loop_backend.Concert.domain.ConcertCategory;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "artists")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Artist {

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

    public void linkItunes(Long itunesArtistId, String artistViewUrl) {
        this.itunesArtistId = itunesArtistId;
        this.artistViewUrl = artistViewUrl;
    }
}
