package com.loop.loop_backend.Artist.domain;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public void update(String name, String baseName, String nameKo, String nameAlias, String imageUrl) {
        this.name = name;
        this.baseName = baseName;
        this.nameKo = nameKo;
        this.nameAlias = nameAlias;
        this.imageUrl = imageUrl;
    }

    public void updateAutoFetch(boolean autoFetchConcerts) {
        this.autoFetchConcerts = autoFetchConcerts;
    }
}
